package com.creker.screentime.ui.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.creker.screentime.ui.theme.CrekerScreenTimeTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the settings page in the two states that read differently: everything working,
 * and the two things that can be off — usage access missing, sharing switched off — which
 * are also the two cases whose explanatory text changes rather than just a control's
 * position.
 */
class SettingsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun settings_allGranted() {
        paparazzi.snapshot {
            CrekerScreenTimeTheme {
                Surface(modifier = Modifier) {
                    SettingsScreen(
                        hasUsageAccess = true,
                        sharingEnabled = true,
                        onBack = {},
                        onOpenUsageAccessSettings = {},
                        onSharingChange = {},
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
                        sharingEnabled = false,
                        onBack = {},
                        onOpenUsageAccessSettings = {},
                        onSharingChange = {},
                        onExport = {},
                        onImport = {},
                    )
                }
            }
        }
    }
}
