package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders real screens to PNGs on the JVM (no emulator) so they can be inspected —
 * by a person, or fetched as a CI artifact and read directly — without a device.
 * Not a pass/fail regression check; `recordPaparazziDebug` is what CI runs.
 */
class ChartScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun minutes(value: Int) = value * 60_000L

    private val hourlyPoints = (0..23).map { hour ->
        val value = when (hour) {
            0 -> minutes(19)
            9 -> minutes(59)
            10 -> minutes(49)
            11 -> minutes(51)
            12 -> minutes(22)
            else -> 0L
        }
        ChartPoint(label = hour.toString(), detailLabel = "%02d:00".format(hour), value = value)
    }

    @Test
    fun chartCard_hourlyBars_darkTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface {
                    Box(Modifier.padding(16.dp)) {
                        ChartCard(
                            metric = ChartMetric.USAGE,
                            onMetricChange = {},
                            chartPoints = hourlyPoints,
                            totalUsageMillis = hourlyPoints.sumOf { it.value },
                        )
                    }
                }
            }
        }
    }

    // Temporary: isolates whether an oversized child of a TopStart-aligned,
    // clipToBounds() Box really renders starting at the box's own origin (red stripe
    // visible at the left edge, nothing else) or is shifted/centered somehow (blue or
    // green visible instead) -- independent of any of the chart's own pan/zoom logic.
    @Test
    fun debugOversizedChildAlignment() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(190.dp)
                            .clipToBounds(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .requiredWidth(800.dp)
                                .requiredHeight(190.dp),
                        ) {
                            drawRect(color = Color.Red, topLeft = Offset.Zero, size = Size(20.dp.toPx(), size.height))
                            drawRect(color = Color.Blue, topLeft = Offset(400.dp.toPx(), 0f), size = Size(20.dp.toPx(), size.height))
                            drawRect(color = Color.Green, topLeft = Offset(780.dp.toPx(), 0f), size = Size(20.dp.toPx(), size.height))
                        }
                    }
                }
            }
        }
    }

    // Same as above, minus clipToBounds() -- isolates whether the clip itself is
    // somehow the thing recentering the child, rather than plain Box placement.
    @Test
    fun debugOversizedChildAlignment_noClip() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(190.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .requiredWidth(800.dp)
                                .requiredHeight(190.dp),
                        ) {
                            drawRect(color = Color.Red, topLeft = Offset.Zero, size = Size(20.dp.toPx(), size.height))
                            drawRect(color = Color.Blue, topLeft = Offset(400.dp.toPx(), 0f), size = Size(20.dp.toPx(), size.height))
                            drawRect(color = Color.Green, topLeft = Offset(780.dp.toPx(), 0f), size = Size(20.dp.toPx(), size.height))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun chartCard_hourlyBars_lightTheme() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = false) {
                Surface {
                    Box(Modifier.padding(16.dp)) {
                        ChartCard(
                            metric = ChartMetric.USAGE,
                            onMetricChange = {},
                            chartPoints = hourlyPoints,
                            totalUsageMillis = hourlyPoints.sumOf { it.value },
                        )
                    }
                }
            }
        }
    }
}
