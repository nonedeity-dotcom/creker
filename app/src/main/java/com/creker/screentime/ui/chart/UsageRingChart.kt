package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.creker.screentime.ui.theme.MonoNumeric

/**
 * One slice of the ring: an app's share of the period's total, 0f..1f.
 *
 * `icon` is not drawn on the arc any more — it belongs to the legend row that carries the
 * same colour — but it stays on the slice so the two are built from one list and cannot
 * fall out of step.
 */
data class RingSlice(
    val label: String,
    val icon: ImageBitmap?,
    val share: Float,
)

private val RING_INSET = 22.dp
private val RING_STROKE = 16.dp

/** Degrees trimmed off each arc so neighbouring slices read as separate bands. */
private const val SLICE_GAP_DEGREES = 3f

/**
 * A donut split into one arc per app, with the period's total in the middle.
 *
 * The arcs run down a single warm ramp, brightest first, rather than through eight
 * distinct hues. Two reasons. A rainbow carried no meaning here — the colours only said
 * "different", never "bigger" — and it was the loudest thing on the screen of an app whose
 * whole point is a calm read of where the day went. A ramp says the one thing the ring is
 * for without a legend: the brightest band is the biggest.
 *
 * The icons used to sit on the arcs, straddling the boundary between two of them, which
 * made every icon look like it belonged to neither. Identity now lives in the list below
 * the ring, where each row carries the same colour as its arc.
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
                            color = ringColor(index),
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
    }
}

/**
 * The ring's ramp: the app's own amber, stepping down towards the warm grey of its
 * chrome. Ordered, not assorted — position in this list is position in the ring.
 *
 * It stops well short of the background. A step that faded all the way out would make the
 * smallest slice indistinguishable from the empty track, which reads as missing data
 * rather than as a small number.
 */
private val RingPalette = listOf(
    Color(0xFFE9A63C),
    Color(0xFFD69B4A),
    Color(0xFFBE8C52),
    Color(0xFFA17A55),
    Color(0xFF876753),
    Color(0xFF6B554A),
)

/** The colour of the nth arc, so a legend row can be painted to match. */
fun ringColor(index: Int): Color = RingPalette[index % RingPalette.size]
