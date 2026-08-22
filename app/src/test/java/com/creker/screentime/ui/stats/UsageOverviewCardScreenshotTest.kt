package com.creker.screentime.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * Checks the subtitle row specifically: "22 авг. 2026 г. • Приложений: 25" wrapped
 * mid-word ("Приложени" / "й: 25") once the mode toggle beside it left less room --
 * the exact appCount (25) and date the user hit this with.
 */
class UsageOverviewCardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val hourlyPoints = (0..23).map { hour ->
        ChartPoint(label = hour.toString(), detailLabel = "%02d:00".format(hour), value = if (hour == 0) 1_800_000L else 0L)
    }

    @Test
    fun usageOverviewCard_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface {
                    Box(Modifier.padding(16.dp)) {
                        UsageOverviewCard(
                            metric = ChartMetric.USAGE,
                            onMetricChange = {},
                            range = DayRange(LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 22)),
                            appCount = 25,
                            chartPoints = hourlyPoints,
                            totalMillis = 32_308_000L,
                            usageChange = UsageComparison(percent = 32, isDecrease = true, comparedToYesterday = true),
                        )
                    }
                }
            }
        }
    }
}
