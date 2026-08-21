package com.creker.screentime.ui.stats

import androidx.compose.ui.graphics.ImageBitmap
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.StatsPeriod
import java.time.LocalDate

/** One point on the usage chart: an hour of the current day, or a day of a longer period. */
data class ChartPoint(val label: String, val usageMillis: Long)

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
    val period: StatsPeriod = StatsPeriod.Day,
    val range: DayRange = DayRange(LocalDate.now(), LocalDate.now()),
    val totalMillis: Long = 0L,
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
