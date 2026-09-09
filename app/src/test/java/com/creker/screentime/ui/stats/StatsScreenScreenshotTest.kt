package com.creker.screentime.ui.stats

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
 * The screen the app opens on, whole.
 *
 * Everything else here had a snapshot except this one, which is the one people actually
 * look at — so a change to its layout could only be judged by installing the APK. The data
 * is a plausible day rather than a tidy one: a long app name, a dominant first app and a
 * tail of small ones, and a chart that stops partway through because the day is not over.
 */
class StatsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val today = LocalDate.of(2026, 9, 6)

    private val hours = listOf(
        0L, 0L, 0L, 0L, 0L, 0L, 0L, 8L, 26L, 41L, 12L, 5L, 34L, 18L, 7L, 22L,
    ).mapIndexed { hour, minutes ->
        ChartPoint(
            label = hour.toString(),
            detailLabel = "%02d:00–%02d:00".format(hour, hour + 1),
            value = minutes * 60_000L,
        )
    }

    private val apps = listOf(
        Triple("TikTok", 2 * 3_600_000L + 22 * 60_000L, "com.zhiliaoapp.musically"),
        Triple("Instagram", 3_600_000L + 36 * 60_000L, "com.instagram.android"),
        Triple("YouTube", 3_600_000L + 14 * 60_000L, "com.google.android.youtube"),
        Triple("Телеграм", 47 * 60_000L, "org.telegram.messenger"),
        Triple("Сбербанк Онлайн", 12 * 60_000L, "ru.sberbankmobile"),
        Triple("Карты", 6 * 60_000L, "ru.yandex.yandexmaps"),
    ).let { rows ->
        val top = rows.maxOf { it.second }
        val total = rows.sumOf { it.second }
        rows.map { (label, millis, pkg) ->
            AppUsageUi(
                packageName = pkg,
                label = label,
                icon = null,
                usageMillis = millis,
                shareOfTop = millis.toFloat() / top,
                shareOfTotal = millis.toFloat() / total,
            )
        }
    }

    private val state = StatsUiState(
        metric = ChartMetric.USAGE,
        range = DayRange(today, today),
        canGoForward = false,
        totalMillis = apps.sumOf { it.usageMillis },
        usageChange = UsageComparison(percent = 18, isDecrease = true, comparedToYesterday = true),
        apps = apps,
        chartPoints = hours,
        earliestStoredDay = LocalDate.of(2026, 8, 20),
        isInitialLoading = false,
        isRefreshing = false,
    )

    @Test
    fun statsScreen_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                StatsScreen(
                    state = state,
                    today = today,
                    onSelectPeriod = {},
                    onSelectCustomRange = { _, _ -> },
                    onPrevious = {},
                    onNext = {},
                    onRefresh = {},
                    onAppClick = {},
                    onSelectMetric = {},
                    onOpenSettings = {},
                    onOpenTotalTime = {},
                )
            }
        }
    }

    @Test
    fun statsScreen_empty_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                StatsScreen(
                    state = state.copy(apps = emptyList(), chartPoints = emptyList(), totalMillis = 0L, usageChange = null),
                    today = today,
                    onSelectPeriod = {},
                    onSelectCustomRange = { _, _ -> },
                    onPrevious = {},
                    onNext = {},
                    onRefresh = {},
                    onAppClick = {},
                    onSelectMetric = {},
                    onOpenSettings = {},
                    onOpenTotalTime = {},
                )
            }
        }
    }
}
