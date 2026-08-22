package com.creker.screentime.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Two voices. Durations and counts are set in monospace: this is a clock, and its
 * digits change constantly — proportional figures make the readout jitter as the
 * seconds tick, monospaced ones hold still. Everything that is prose stays in the
 * platform's own face.
 *
 * Use [MonoNumeric] directly for any number that updates in place.
 */
val MonoNumeric = FontFamily.Monospace

private val Default = Typography()

val CrekerTypography = Typography(
    // The headline readout — the one number the whole screen is built around.
    displayMedium = TextStyle(
        fontFamily = MonoNumeric,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
    ),
    // Section eyebrows above it: small, wide-tracked, quiet.
    labelLarge = Default.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = Default.labelSmall.copy(letterSpacing = 0.4.sp),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(
        fontFamily = MonoNumeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
        textAlign = TextAlign.Start,
    ),
)
