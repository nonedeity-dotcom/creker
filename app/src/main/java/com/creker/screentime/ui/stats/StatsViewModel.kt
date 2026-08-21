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
import com.creker.screentime.core.StatsPeriod
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
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
) : ViewModel() {

    private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Day)
    private val metric = MutableStateFlow(ChartMetric.USAGE)
    private val refreshing = MutableStateFlow(false)

    /**
     * Bumped after every sync. "Today" is relative to the wall clock, so the day range
     * has to be resolved again rather than reused from when the period was picked.
     */
    private val resolveTicket = MutableStateFlow(0)

    private data class ScreenData(
        val period: StatsPeriod,
        val metric: ChartMetric,
        val range: DayRange,
        val rows: List<AppUsageTotal>,
        val chartPoints: List<ChartPoint>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenData: Flow<ScreenData> =
        combine(period, metric, resolveTicket) { selectedPeriod, selectedMetric, _ -> selectedPeriod to selectedMetric }
            .flatMapLatest { (selectedPeriod, selectedMetric) ->
                val range = selectedPeriod.resolve(repository.today())
                combine(repository.observeTotals(range), chartPointsFlow(range, selectedMetric)) { rows, points ->
                    ScreenData(selectedPeriod, selectedMetric, range, rows, points)
                }
            }

    /**
     * A single day gets an hour-by-hour chart, since that is the only granularity worth
     * plotting within one day; anything longer gets one point per day. The single-day
     * case is restricted to today specifically — Room only stores daily totals, so an
     * hourly breakdown of a past day would need events the system has likely already
     * discarded and would render as a misleadingly flat chart.
     */
    private fun chartPointsFlow(range: DayRange, metric: ChartMetric): Flow<List<ChartPoint>> {
        val isToday = range.dayCount == 1 && range.from == repository.today()
        val useWeekdayLabels = range.dayCount <= 8
        return when (metric) {
            ChartMetric.USAGE -> if (isToday) {
                flow { emit(repository.hourlyBreakdown(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SESSIONS -> if (isToday) {
                flow { emit(repository.hourlySessions(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeSessionDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SCREEN_TIME -> if (isToday) {
                flow { emit(repository.hourlyScreenTime(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeDeviceDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }
        }
    }

    val uiState: StateFlow<StatsUiState> =
        combine(screenData, repository.observeEarliestDay(), refreshing) { data, earliest, isRefreshing ->
            StatsUiState(
                period = data.period,
                metric = data.metric,
                range = data.range,
                totalMillis = data.rows.sumOf { it.usageMillis },
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
        period.value = newPeriod
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        period.value = StatsPeriod.Custom(from, to)
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
                resolveTicket.value += 1
            } finally {
                refreshing.value = false
            }
        }
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
