package com.creker.screentime.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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

    // Temporary: the hourly chart's axis labels still collide as if the widening fix
    // (MIN_SLOT_WIDTH driving a canvas wider than the viewport) never took effect.
    // This isolates BoxWithConstraints.maxWidth from everything else to see what
    // Paparazzi is actually reporting as the available width.
    @Test
    fun debugMaxWidth() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.padding(16.dp)) {
                        Text("maxWidth=$maxWidth", color = Color.White)
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
