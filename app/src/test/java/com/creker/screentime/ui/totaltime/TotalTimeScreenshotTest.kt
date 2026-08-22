package com.creker.screentime.ui.totaltime

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.UsageComparison
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Renders the full total-time page: ring chart, time-saved card, and app-analysis grid. */
class TotalTimeScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun minutes(value: Int) = value * 60_000L

    private val today = LocalDate.of(2026, 8, 22)

    private val apps = listOf(
        TotalTimeAppUi(
            packageName = "com.zhiliaoapp.musically",
            label = "TikTok",
            icon = null,
            usageMillis = minutes(142),
            change = UsageComparison(percent = 18, isDecrease = true, comparedToYesterday = false),
            shareOfTotal = 0.38f,
        ),
        TotalTimeAppUi(
            packageName = "com.instagram.android",
            label = "Instagram",
            icon = null,
            usageMillis = minutes(96),
            change = UsageComparison(percent = 12, isDecrease = false, comparedToYesterday = false),
            shareOfTotal = 0.26f,
        ),
        TotalTimeAppUi(
            packageName = "com.google.android.youtube",
            label = "YouTube",
            icon = null,
            usageMillis = minutes(74),
            change = UsageComparison(percent = 5, isDecrease = true, comparedToYesterday = false),
            shareOfTotal = 0.20f,
        ),
        TotalTimeAppUi(
            packageName = "com.whatsapp",
            label = "WhatsApp",
            icon = null,
            usageMillis = minutes(60),
            change = null,
            shareOfTotal = 0.16f,
        ),
    )

    private val state = TotalTimeUiState(
        range = DayRange(today, today),
        canGoForward = false,
        totalMillis = apps.sumOf { it.usageMillis },
        apps = apps,
        savedMillis = minutes(22) + 38_000L,
        isInitialLoading = false,
    )

    @Test
    fun totalTimeScreen_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier) {
                    TotalTimeScreen(
                        state = state,
                        today = today,
                        onBack = {},
                        onPrevious = {},
                        onNext = {},
                        onSelectPeriod = {},
                        onSelectCustomRange = { _, _ -> },
                        onAppClick = {},
                    )
                }
            }
        }
    }

    /**
     * The real case that broke the ring: a phone with two dozen tracked apps, most of
     * them a fraction of a percent. Every slice used to be inflated to a visible
     * minimum, so the arcs summed well past 360 and wrapped back over the start, with
     * every icon stacked on its neighbours.
     */
    @Test
    fun totalTimeScreen_manyApps_darkTheme() {
        // A realistic long tail: four apps carry most of the time, twenty share the rest.
        val heavy = listOf("TikTok" to 142, "Instagram" to 96, "YouTube" to 74, "WhatsApp" to 60)
        val tail = (1..20).map { "App $it" to 2 }
        val all = heavy + tail
        val totalMinutes = all.sumOf { it.second }
        val manyApps = all.mapIndexed { index, (name, mins) ->
            TotalTimeAppUi(
                packageName = "com.example.app$index",
                label = name,
                icon = null,
                usageMillis = minutes(mins),
                change = if (index % 3 == 0) UsageComparison(7, index % 2 == 0, false) else null,
                shareOfTotal = mins.toFloat() / totalMinutes,
            )
        }

        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier) {
                    TotalTimeScreen(
                        state = state.copy(
                            apps = manyApps,
                            totalMillis = minutes(totalMinutes),
                        ),
                        today = today,
                        onBack = {},
                        onPrevious = {},
                        onNext = {},
                        onSelectPeriod = {},
                        onSelectCustomRange = { _, _ -> },
                        onAppClick = {},
                    )
                }
            }
        }
    }
}
