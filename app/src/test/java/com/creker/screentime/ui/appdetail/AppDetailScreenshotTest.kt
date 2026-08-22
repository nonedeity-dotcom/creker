package com.creker.screentime.ui.appdetail

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageComparison
import com.creker.screentime.ui.chart.ChartPoint
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Renders the full app-detail page (not just ChartCard) so the stat-tile grid below
 * the chart can be checked for real, rather than guessed at from a description.
 */
class AppDetailScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun minutes(value: Int) = value * 60_000L

    private val hourlyPoints = (0..23).map { hour ->
        ChartPoint(label = hour.toString(), detailLabel = "%02d:00".format(hour), value = if (hour == 0) minutes(5) else 0L)
    }

    private val today = LocalDate.of(2026, 8, 22)

    private val state = AppDetailUiState(
        packageName = "com.zhiliaoapp.musically",
        label = "TikTok",
        installedAtMs = LocalDate.of(2025, 10, 28).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        range = DayRange(today, today),
        canGoForward = false,
        usageMillis = 2 * 3_600_000L + 29 * 60_000L + 41_000L,
        usageChange = UsageComparison(percent = 18, isDecrease = true, comparedToYesterday = true),
        launchCount = 26,
        averagePerDayMillis = 2 * 3_600_000L + 29 * 60_000L + 41_000L,
        currentStreakDays = 8,
        longestStreakDays = 8,
        maxDayMillis = 5 * 3_600_000L + 21 * 60_000L + 7_000L,
        metric = ChartMetric.USAGE,
        chartPoints = hourlyPoints,
        isLoading = false,
    )

    @Test
    fun appDetailScreen_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier) {
                    AppDetailScreen(
                        state = state,
                        today = today,
                        onBack = {},
                        onPrevious = {},
                        onNext = {},
                        onSelectRange = {},
                        onSelectMetric = {},
                    )
                }
            }
        }
    }
}
