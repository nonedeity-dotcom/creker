package com.creker.screentime.ui.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.creker.screentime.data.settings.CallerAccess
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the settings page in the two states that read differently: everything working,
 * and the things that can be off — usage access missing, the companion app refused, a
 * stranger that asked and was turned away — which are also the cases whose explanatory
 * text changes rather than just a control's position.
 */
class SettingsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    /** Fixed so the rendered "last request" line doesn't change from run to run. */
    private val LAST_SEEN_MS = 1_756_800_000_000L

    @Test
    fun settings_allGranted() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme {
                Surface(modifier = Modifier) {
                    SettingsScreen(
                        hasUsageAccess = true,
                        callers = listOf(
                            CallerUi(
                                packageName = CallerAccess.NO_BURNOUT_PACKAGE,
                                label = "No Burnout",
                                isInstalled = true,
                                allowed = true,
                                lastSeenMs = LAST_SEEN_MS,
                            ),
                        ),
                        onBack = {},
                        onOpenUsageAccessSettings = {},
                        onCallerAllowedChange = { _, _ -> },
                        onExport = {},
                        onImport = {},
                    )
                }
            }
        }
    }

    @Test
    fun settings_noAccessSharingOff() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme {
                Surface(modifier = Modifier) {
                    SettingsScreen(
                        hasUsageAccess = false,
                        callers = listOf(
                            CallerUi(
                                packageName = CallerAccess.NO_BURNOUT_PACKAGE,
                                label = "No Burnout",
                                isInstalled = true,
                                allowed = false,
                                lastSeenMs = LAST_SEEN_MS,
                            ),
                            CallerUi(
                                packageName = "com.example.someapp",
                                label = "Some App",
                                isInstalled = true,
                                allowed = false,
                                lastSeenMs = LAST_SEEN_MS - 3_600_000L,
                            ),
                            CallerUi(
                                packageName = "com.example.gone",
                                label = "com.example.gone",
                                isInstalled = false,
                                allowed = false,
                                lastSeenMs = 0L,
                            ),
                        ),
                        onBack = {},
                        onOpenUsageAccessSettings = {},
                        onCallerAllowedChange = { _, _ -> },
                        onExport = {},
                        onImport = {},
                    )
                }
            }
        }
    }
}
