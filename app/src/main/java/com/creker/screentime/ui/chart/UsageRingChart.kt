package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.creker.screentime.ui.theme.MonoNumeric
import kotlin.math.cos
import kotlin.math.sin

/** One slice of the ring chart: an app's share of the period's total, 0f..1f. */
data class RingSlice(
    val label: String,
    val icon: ImageBitmap?,
    val share: Float,
)

private val RING_INSET = 22.dp
private val RING_STROKE = 16.dp
private val ICON_SIZE = 26.dp

/** Degrees trimmed off each arc so neighbouring slices read as separate bands. */
private const val SLICE_GAP_DEGREES = 3f

/**
 * A donut chart split into one arc per app, its icon sitting just outside the ring at
 * that arc's midpoint, with the period's total spelled out in the center. Distinct
 * hues per slice, cycling if there are more apps than colors -- the app's own single
 * amber accent can't tell neighbouring slices apart the way this needs to.
 *
 * Callers are expected to have already grouped a long tail of tiny slices into one
 * (see TotalTimeScreen): shares are drawn exactly as given, so the arcs only add up
 * to a full circle if the shares themselves do.
 */
@Composable
fun UsageRingChart(slices: List<RingSlice>, totalLabel: String, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        val ringDiameter = diameter - RING_INSET * 2
        val iconRadius = ringDiameter / 2

        Canvas(
            modifier = Modifier
                .size(ringDiameter)
                .align(Alignment.Center),
        ) {
            val stroke = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Butt)
            if (slices.isEmpty() || slices.all { it.share <= 0f }) {
                drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            } else {
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = 360f * slice.share
                    // No minimum sweep: inflating a sub-degree slice to a visible one
                    // made every slice past the first few overshoot, so with a couple
                    // dozen apps the arcs summed past 360 and wrapped back over the
                    // start of the ring. A slice too thin to draw is simply skipped --
                    // the caller groups those into one "other" slice instead.
                    if (sweep > SLICE_GAP_DEGREES) {
                        drawArc(
                            color = RingPalette[index % RingPalette.size],
                            startAngle = startAngle + SLICE_GAP_DEGREES / 2f,
                            sweepAngle = sweep - SLICE_GAP_DEGREES,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                    startAngle += sweep
                }
            }
        }

        Text(
            text = totalLabel,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoNumeric),
            color = onSurface,
        )

        // An icon needs roughly its own width of arc to sit in without colliding with
        // its neighbour's. Anything thinner is left unlabelled rather than stacked.
        val minShareForIcon = if (iconRadius > 0.dp) {
            (ICON_SIZE.value * 1.15f) / (2f * Math.PI.toFloat() * iconRadius.value)
        } else {
            Float.MAX_VALUE
        }

        var iconStartAngle = -90f
        slices.forEach { slice ->
            val sweep = 360f * slice.share
            val midAngleDeg = iconStartAngle + sweep / 2f
            iconStartAngle += sweep
            if (slice.share < minShareForIcon) return@forEach

            // A plain (non-remember()) computation: remember() inside a loop over a
            // list whose size can change between recompositions risks misaligning
            // Compose's slot table, and cos/sin here are cheap enough not to need it.
            val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
            val offsetX = iconRadius * cos(midAngleRad).toFloat()
            val offsetY = iconRadius * sin(midAngleRad).toFloat()

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY)
                    .size(ICON_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val icon = slice.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = slice.label,
                        modifier = Modifier.size(22.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = slice.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private val RingPalette = listOf(
    Color(0xFFE9A63C),
    Color(0xFF6FA8DC),
    Color(0xFF5FB86A),
    Color(0xFFE9795B),
    Color(0xFFB07CC6),
    Color(0xFF4FB0AE),
    Color(0xFFD4708B),
    Color(0xFFE0C368),
)
