package com.creker.screentime.ui.appdetail

import androidx.compose.ui.graphics.ImageBitmap
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageComparison
import com.creker.screentime.ui.chart.ChartPoint
import java.time.LocalDate

data class AppDetailUiState(
    val packageName: String,
    val label: String = "",
    val icon: ImageBitmap? = null,
    val installedAtMs: Long? = null,
    val range: DayRange = DayRange(LocalDate.now(), LocalDate.now()),
    /** False once stepping forward would move the range's end past today. */
    val canGoForward: Boolean = false,
    val usageMillis: Long = 0L,
    val usageChange: UsageComparison? = null,
    val launchCount: Int = 0,
    val averagePerDayMillis: Long = 0L,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val maxDayMillis: Long = 0L,
    val metric: ChartMetric = ChartMetric.USAGE,
    val chartPoints: List<ChartPoint> = emptyList(),
    val isLoading: Boolean = true,
)
