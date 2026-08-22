package com.creker.screentime.ui.stats

import androidx.compose.ui.graphics.ImageBitmap
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.ui.chart.ChartPoint
import java.time.LocalDate

/**
 * Total usage vs. the immediately preceding period of the same length — e.g. today
 * vs. yesterday, or this week vs. last week. Absent when there is no prior period to
 * compare against (nothing stored for it yet).
 */
data class UsageComparison(
    /** Always positive; [isDecrease] carries the direction. */
    val percent: Int,
    val isDecrease: Boolean,
    /** True for a single-day period ("than yesterday"); false for anything longer. */
    val comparedToYesterday: Boolean,
)

/** One row of the app list. */
data class AppUsageUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val usageMillis: Long,
    /** Share of the most-used app's time, used for the bar width. */
    val shareOfTop: Float,
    /** Share of the period's total time, shown as a percentage next to the row. */
    val shareOfTotal: Float,
)

data class StatsUiState(
    val metric: ChartMetric = ChartMetric.USAGE,
    val range: DayRange = DayRange(LocalDate.now(), LocalDate.now()),
    /** False once stepping forward would move the range's end past today. */
    val canGoForward: Boolean = false,
    /** Time spent in apps over the period — always app usage, whatever [metric] shows. */
    val totalMillis: Long = 0L,
    val usageChange: UsageComparison? = null,
    val apps: List<AppUsageUi> = emptyList(),
    val chartPoints: List<ChartPoint> = emptyList(),
    /** First day the local history covers, `null` until anything is stored. */
    val earliestStoredDay: LocalDate? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
) {
    /**
     * True when the selected period reaches further back than the local history does,
     * so the user knows the numbers are not a full picture yet.
     */
    val hasIncompleteHistory: Boolean
        get() = earliestStoredDay != null && earliestStoredDay.isAfter(range.from)
}
