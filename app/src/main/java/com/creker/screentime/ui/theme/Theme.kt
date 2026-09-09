package com.creker.screentime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * The palette still comes off the launcher icon — a baked biscuit — but the ground it sits
 * on is no longer made of the same biscuit.
 *
 * Everything used to be brown: the background, the cards, the dividers, and the text, which
 * was cream. A screen where every surface is one muddy hue and nothing is properly white
 * reads as washed out no matter how the layout is arranged, and it left the accent with
 * nothing to stand out against. So: a near-black ground with only a hint of warmth in it,
 * cards a clear step lighter so they actually look raised, text close to white, and the
 * amber turned up and spent only where it means something — the number you came to read,
 * the bar that says how much, the control that is currently on.
 *
 * Dynamic (wallpaper) colour is deliberately NOT used — it overrode this entirely on
 * Android 12+, which is why the app came out looking like whatever wallpaper happened to be
 * set rather than like itself.
 */

// --- the accent, and the two tones either side of it ------------------------------
private val Ember = Color(0xFFFFB340)      // brighter than the old #E9A63C, and the only loud thing
private val EmberSoft = Color(0xFFFFD08A)  // its lit edge, for text on a dark amber fill
private val EmberDeep = Color(0xFF573A0C)  // amber pressed into the dark, for filled chips
private val Scorch = Color(0xFF2B1A00)     // what sits *on* the accent

// --- the ground: warm, but barely -------------------------------------------------
// One ladder, each rung lighter than the last. The first attempt had dividers lighter than
// the controls sitting on them, which is how a surface stops reading as a surface.
private val Night = Color(0xFF131011)      // background and surface
private val Raised = Color(0xFF1E1A18)     // cards
private val Higher = Color(0xFF2A2420)     // controls resting on a card, empty track
private val Hairline = Color(0xFF322B27)   // dividers
private val Edge = Color(0xFF55493F)       // outlines

// --- type -------------------------------------------------------------------------
private val Bright = Color(0xFFF3EFEA)     // near-white: the old cream was the main reason
                                           // the whole app looked faded
private val Muted = Color(0xFFA79D93)      // secondary text

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Scorch,
    primaryContainer = EmberDeep,
    onPrimaryContainer = EmberSoft,
    secondary = EmberSoft,
    onSecondary = Scorch,
    background = Night,
    onBackground = Bright,
    surface = Night,
    onSurface = Bright,
    surfaceContainer = Raised,
    surfaceContainerHigh = Higher,
    surfaceContainerHighest = Higher,
    surfaceVariant = Higher,
    onSurfaceVariant = Muted,
    outline = Edge,
    outlineVariant = Hairline,
    inverseSurface = Bright,
    inverseOnSurface = Night,
    error = Color(0xFFFF7A66),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2E1A00),
    secondary = Color(0xFF6F5B43),
    onSecondary = Color.White,
    background = Color(0xFFFFFBF6),
    onBackground = Color(0xFF1C1815),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1C1815),
    surfaceContainer = Color(0xFFF6EFE6),
    surfaceContainerHigh = Color(0xFFEFE6DA),
    surfaceContainerHighest = Color(0xFFEFE6DA),
    surfaceVariant = Color(0xFFE7DCCE),
    onSurfaceVariant = Color(0xFF564A3E),
    outline = Color(0xFF8A7A69),
    outlineVariant = Color(0xFFDCD0C0),
    inverseSurface = Color(0xFF322A24),
    inverseOnSurface = Color(0xFFFFF3E6),
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
