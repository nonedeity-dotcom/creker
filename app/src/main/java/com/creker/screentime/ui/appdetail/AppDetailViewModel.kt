package com.creker.screentime.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.creker.screentime.AppContainer
import com.creker.screentime.core.AppPeriodTotal
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageStreaks
import com.creker.screentime.core.shiftBy
import com.creker.screentime.core.usageChange
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
import java.time.LocalDate

/**
 * One app's detail screen: usage and launches over a steppable date window, plus
 * streak and peak stats drawn from the app's whole locally retained history.
 */
class AppDetailViewModel(
    private val repository: UsageRepository,
    private val appInfoProvider: AppInfoProvider,
    private val packageName: String,
    initialRange: DayRange,
) : ViewModel() {

    private val range = MutableStateFlow(initialRange)
    private val metric = MutableStateFlow(ChartMetric.USAGE)

    private data class ScreenData(
        val range: DayRange,
        val metric: ChartMetric,
        val total: AppPeriodTotal,
        val previousUsageMillis: Long,
        val chartPoints: List<ChartPoint>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenData: Flow<ScreenData> =
        combine(range, metric) { r, m -> r to m }
            .flatMapLatest { (currentRange, currentMetric) ->
                val previousRange = currentRange.shiftBy(-currentRange.dayCount)
                combine(
                    repository.observeAppPeriodTotal(packageName, currentRange),
                    repository.observeAppPeriodTotal(packageName, previousRange),
                    chartPointsFlow(currentRange, currentMetric),
                ) { total, previousTotal, points ->
                    ScreenData(currentRange, currentMetric, total, previousTotal.usageMillis, points)
                }
            }

    /**
     * Usage and sessions are this one app's own numbers; screen time is device-wide
     * and shared verbatim with the overview screen, since it is not tied to any
     * particular app. As on the overview chart, the hourly case is restricted to the
     * same rolling window sync() itself relies on for the system's raw event log
     * (IMPORT_WINDOW_DAYS) — Room does not store hourly history, and further back the
     * live log has likely already aged out.
     */
    private fun chartPointsFlow(range: DayRange, metric: ChartMetric): Flow<List<ChartPoint>> {
        val isRecentSingleDay = range.dayCount == 1 &&
            !range.from.isBefore(repository.today().minusDays(UsageRepository.IMPORT_WINDOW_DAYS))
        val useWeekdayLabels = range.dayCount <= 8
        return when (metric) {
            ChartMetric.USAGE -> if (isRecentSingleDay) {
                flow { emit(repository.hourlyBreakdownForApp(range.from, packageName).toHourlyChartPoints()) }
            } else {
                repository.observeAppDailyUsage(packageName, range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SESSIONS -> if (isRecentSingleDay) {
                flow { emit(repository.hourlySessionsForApp(range.from, packageName).toHourlyChartPoints()) }
            } else {
                repository.observeAppDailySessions(packageName, range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }

            ChartMetric.SCREEN_TIME -> if (isRecentSingleDay) {
                flow { emit(repository.hourlyScreenTime(range.from).toHourlyChartPoints()) }
            } else {
                repository.observeDeviceDailyTotals(range).map { it.toDailyChartPoints(useWeekdayLabels) }
            }
        }
    }

    val uiState: StateFlow<AppDetailUiState> =
        combine(screenData, repository.observeAppHistory(packageName)) { data, history ->
            val today = repository.today()
            val info = appInfoProvider.get(packageName)
            AppDetailUiState(
                packageName = packageName,
                label = info.label,
                icon = info.icon,
                installedAtMs = info.installedAtMs,
                range = data.range,
                canGoForward = !data.range.shiftBy(data.range.dayCount).to.isAfter(today),
                usageMillis = data.total.usageMillis,
                usageChange = usageChange(data.total.usageMillis, data.previousUsageMillis, data.range.dayCount),
                launchCount = data.total.launchCount,
                averagePerDayMillis = data.total.usageMillis / data.range.dayCount,
                currentStreakDays = UsageStreaks.currentStreak(history, today),
                longestStreakDays = UsageStreaks.longestStreak(history),
                maxDayMillis = UsageStreaks.maxDayUsage(history),
                metric = data.metric,
                chartPoints = data.chartPoints,
                isLoading = false,
            )
        }
            // Streak/average math and label resolution must not happen on the main thread.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AppDetailUiState(packageName = packageName, range = initialRange),
            )

    fun goToPreviousPeriod() {
        range.update { it.shiftBy(-it.dayCount) }
    }

    fun goToNextPeriod() {
        range.update { current ->
            val next = current.shiftBy(current.dayCount)
            if (next.to.isAfter(repository.today())) current else next
        }
    }

    /** Jumps straight to a specific window — used by the period picker's presets and custom range. */
    fun selectRange(newRange: DayRange) {
        range.value = newRange
    }

    fun selectMetric(newMetric: ChartMetric) {
        metric.value = newMetric
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(
            container: AppContainer,
            packageName: String,
            initialRange: DayRange,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppDetailViewModel(container.usageRepository, container.appInfoProvider, packageName, initialRange)
            }
        }
    }
}
