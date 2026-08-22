package com.creker.screentime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Palette taken straight off the launcher icon: a baked biscuit on a dark oven.
 * Dynamic (wallpaper) colour is deliberately NOT used — it overrode this entirely on
 * Android 12+, which is why the app came out looking like whatever wallpaper happened
 * to be set rather than like itself.
 */
private val Dough = Color(0xFFE9A63C)      // the biscuit face
private val DoughLight = Color(0xFFF5BE68) // its lit top edge
private val Crust = Color(0xFFC0741F)      // toasted rim
private val Baked = Color(0xFF2A1C10)      // the dark baked-in lines
private val Oven = Color(0xFF17110C)       // near-black the biscuit sits on
private val Pan = Color(0xFF211913)        // raised surfaces on that ground
private val Char = Color(0xFF3B2E23)       // hairlines and inactive chrome
private val Crumb = Color(0xFFF6D8A8)      // lightest tone, for text on dark

private val DarkColors = darkColorScheme(
    primary = Dough,
    onPrimary = Baked,
    primaryContainer = Color(0xFF4A2E0F),
    onPrimaryContainer = Crumb,
    secondary = Crust,
    onSecondary = Crumb,
    background = Oven,
    onBackground = Crumb,
    surface = Oven,
    onSurface = Crumb,
    surfaceVariant = Char,
    onSurfaceVariant = Color(0xFFC5AE93),
    outline = Color(0xFF6B5641),
    outlineVariant = Char,
    inverseSurface = Crumb,
    inverseOnSurface = Baked,
    error = Color(0xFFE9795B),
)

private val LightColors = lightColorScheme(
    primary = Crust,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2B8),
    onPrimaryContainer = Color(0xFF3A2408),
    secondary = Color(0xFF7A5A32),
    onSecondary = Color.White,
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF241A10),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF241A10),
    surfaceVariant = Color(0xFFF0E2CE),
    onSurfaceVariant = Color(0xFF5A4835),
    outline = Color(0xFF8C7659),
    outlineVariant = Color(0xFFE0D0B8),
    inverseSurface = Color(0xFF3A2C1E),
    inverseOnSurface = Color(0xFFFFF0DC),
    error = Color(0xFFB3301B),
)

@Composable
fun CrekerScreenTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CrekerTypography,
        content = content,
    )
}
