package com.creker.screentime.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.PeriodSelection
import com.creker.screentime.core.StatsPeriod
import com.creker.screentime.core.resolve
import com.creker.screentime.core.shiftBy
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.system.AppInfoProvider
import com.creker.screentime.ui.chart.ChartPoint
import com.creker.screentime.ui.chart.toDailyChartPoints
import com.creker.screentime.ui.chart.toHourlyChartPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class StatsViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
) : ViewModel() {

    private val selection = MutableStateFlow<PeriodSelection>(PeriodSelection.Preset(StatsPeriod.Day))
    private val metric = MutableStateFlow(ChartMetric.USAGE)
    private val refreshing = MutableStateFlow(false)

    /**
     * Bumped after every sync. A preset selection is relative to the wall clock, so it
     * has to be re-resolved rather than reused from when it was picked.
     */
    private val resolveTicket = MutableStateFlow(0)

    private data class ScreenData(
        val metric: ChartMetric,
        val range: DayRange,
        val rows: List<AppUsageTotal>,
        val previousTotalMillis: Long,
        val chartPoints: List<ChartPoint>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenData: Flow<ScreenData> =
        combine(selection, metric, resolveTicket) { selectedSelection, selectedMetric, _ -> selectedSelection to selectedMetric }
            .flatMapLatest { (selectedSelection, selectedMetric) ->
                val range = selectedSelection.resolve(repository.today())
                val previousRange = range.shiftBy(-range.dayCount)
                combine(
                    repository.observeTotals(range),
                    repository.observeTotals(previousRange),
                    chartPointsFlow(range, selectedMetric),
                ) { rows, previousRows, points ->
                    ScreenData(selectedMetric, range, rows, previousRows.sumOf { it.usageMillis }, points)
                }
            }

    /**
     * A single day gets an hour-by-hour chart, since that is the only granularity worth
     * plotting within one day; anything longer gets one point per day. Restricted to
     * the same rolling window sync() itself relies on for the system's raw event log
     * (IMPORT_WINDOW_DAYS) — a live hourly breakdown needs that log directly, and
     * further back it has likely already aged out, which would render as a
     * misleadingly empty chart even though a real (Room-stored) total exists.
     */
    private fun chartPointsFlow(range: DayRange, metric: ChartMetric): Flow<List<ChartPoint>> {
        val isRecentSingleDay = range.dayCount == 1 &&
            !range.from.isBefore(repository.today().minusDays(UsageRepository.IMPORT_WINDOW_DAYS))
        val useWeekdayLabels = range.dayCount <= 8
        return when (metric) {
            ChartMetric.USAGE -> if (isRecentSingleDay) {
                flow { emit(repository.hourlyBreakdown(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SESSIONS -> if (isRecentSingleDay) {
                flow { emit(repository.hourlySessions(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeSessionDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SCREEN_TIME -> if (isRecentSingleDay) {
                flow { emit(repository.hourlyScreenTime(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeDeviceDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }
        }
    }

    val uiState: StateFlow<StatsUiState> =
        combine(screenData, repository.observeEarliestDay(), refreshing) { data, earliest, isRefreshing ->
            val today = repository.today()
            val totalMillis = data.rows.sumOf { it.usageMillis }
            StatsUiState(
                metric = data.metric,
                range = data.range,
                canGoForward = !data.range.shiftBy(data.range.dayCount).to.isAfter(today),
                totalMillis = totalMillis,
                usageChange = usageChange(totalMillis, data.previousTotalMillis, data.range.dayCount),
                apps = data.rows.toUiRows(),
                chartPoints = data.chartPoints,
                earliestStoredDay = earliest,
                isInitialLoading = false,
                isRefreshing = isRefreshing,
            )
        }
            // Resolving labels and decoding icons must not happen on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StatsUiState(),
            )

    fun selectPeriod(newPeriod: StatsPeriod) {
        selection.value = PeriodSelection.Preset(newPeriod)
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        selection.value = PeriodSelection.Fixed(if (from.isAfter(to)) DayRange(to, from) else DayRange(from, to))
    }

    fun goToPreviousPeriod() {
        val current = selection.value.resolve(repository.today())
        selection.value = PeriodSelection.Fixed(current.shiftBy(-current.dayCount))
    }

    fun goToNextPeriod() {
        val today = repository.today()
        val current = selection.value.resolve(today)
        val next = current.shiftBy(current.dayCount)
        if (!next.to.isAfter(today)) {
            selection.value = PeriodSelection.Fixed(next)
        }
    }

    fun selectMetric(newMetric: ChartMetric) {
        metric.value = newMetric
    }

    /** Pulls fresh events from the system into the local database. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.sync()
                resolveTicket.update { it + 1 }
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Null when there is nothing stored for the previous period to compare against. */
    private fun usageChange(current: Long, previous: Long, dayCount: Int): UsageComparison? {
        if (previous <= 0L) return null
        val percent = ((current - previous) * 100.0 / previous).roundToInt()
        if (percent == 0) return null
        return UsageComparison(
            percent = abs(percent),
            isDecrease = percent < 0,
            comparedToYesterday = dayCount == 1,
        )
    }

    private fun List<AppUsageTotal>.toUiRows(): List<AppUsageUi> {
        val top = maxOfOrNull { it.usageMillis } ?: 0L
        val total = sumOf { it.usageMillis }
        return map { row ->
            val info = appInfoProvider.get(row.packageName)
            AppUsageUi(
                packageName = row.packageName,
                label = info.label,
                icon = info.icon,
                usageMillis = row.usageMillis,
                shareOfTop = if (top > 0L) row.usageMillis.toFloat() / top else 0f,
                shareOfTotal = if (total > 0L) row.usageMillis.toFloat() / total else 0f,
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(container.usageRepository, container.appInfoProvider)
            }
        }
    }
}
