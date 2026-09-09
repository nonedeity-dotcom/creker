package com.creker.screentime.ui.totaltime

import androidx.compose.ui.graphics.ImageBitmap
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageComparison
import java.time.LocalDate

/** One app's ring-chart slice and analysis card. */
data class TotalTimeAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val usageMillis: Long,
    /** Null when there is nothing stored for this app in the previous period. */
    val change: UsageComparison?,
    /** This app's share of the period's total usage, for the ring chart's arc. */
    val shareOfTotal: Float,
)

data class TotalTimeUiState(
    val range: DayRange = DayRange(LocalDate.now(), LocalDate.now()),
    /** False once stepping forward would move the range's end past today. */
    val canGoForward: Boolean = false,
    val totalMillis: Long = 0L,
    val apps: List<TotalTimeAppUi> = emptyList(),
    /**
     * How this period differs from the previous equally-long one, in both directions.
     *
     * Was "savedMillis", which existed only when usage went *down*: the screen congratulated
     * one outcome and said nothing about the other. A tracker that only speaks up when the
     * number flatters you is not reporting, it is coaching. Null when there is no prior
     * period to compare against.
     */
    val totalChangeMillis: Long? = null,
    val isInitialLoading: Boolean = true,
)
