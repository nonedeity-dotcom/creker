package com.creker.screentime.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.AppUsageTotal
import com.creker.screentime.core.DailyTotal
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.HourlyUsage
import com.creker.screentime.core.StatsPeriod
import com.creker.screentime.data.UsageRepository
import com.creker.screentime.data.system.AppInfoProvider
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StatsViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
) : ViewModel() {

    private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Day)
    private val refreshing = MutableStateFlow(false)

    /**
     * Bumped after every sync. "Today" is relative to the wall clock, so the day range
     * has to be resolved again rather than reused from when the period was picked.
     */
    private val resolveTicket = MutableStateFlow(0)

    private data class ScreenData(
        val period: StatsPeriod,
        val range: DayRange,
        val rows: List<AppUsageTotal>,
        val chartPoints: List<ChartPoint>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenData: Flow<ScreenData> =
        combine(period, resolveTicket) { selected, _ -> selected }
            .flatMapLatest { selected ->
                val range = selected.resolve(repository.today())
                combine(repository.observeTotals(range), chartPointsFlow(range)) { rows, points ->
                    ScreenData(selected, range, rows, points)
                }
            }

    /**
     * A single day gets an hour-by-hour chart, since that is the only granularity worth
     * plotting within one day; anything longer gets one point per day. The single-day
     * case is restricted to today specifically — Room only stores daily totals, so an
     * hourly breakdown of a past day would need events the system has likely already
     * discarded and would render as a misleadingly flat chart.
     */
    private fun chartPointsFlow(range: DayRange): Flow<List<ChartPoint>> =
        if (range.dayCount == 1 && range.from == repository.today()) {
            flow { emit(repository.hourlyBreakdown(range.from).toChartPoints()) }
        } else {
            repository.observeDailyTotals(range).map { it.toChartPoints(useWeekdayLabels = range.dayCount <= 8) }
        }

    val uiState: StateFlow<StatsUiState> =
        combine(screenData, repository.observeEarliestDay(), refreshing) { data, earliest, isRefreshing ->
            StatsUiState(
                period = data.period,
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

    private fun List<HourlyUsage>.toChartPoints(): List<ChartPoint> =
        map { ChartPoint(label = "%02d:00".format(it.hour), usageMillis = it.usageMillis) }

    private fun List<DailyTotal>.toChartPoints(useWeekdayLabels: Boolean): List<ChartPoint> =
        map { daily ->
            val label = if (useWeekdayLabels) {
                daily.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
                    .replaceFirstChar { it.uppercase() }
            } else {
                daily.day.format(MONTH_DAY_FORMATTER)
            }
            ChartPoint(label = label, usageMillis = daily.usageMillis)
        }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(container.usageRepository, container.appInfoProvider)
            }
        }
    }
}
