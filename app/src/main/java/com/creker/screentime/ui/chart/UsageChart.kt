package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.ui.theme.MonoNumeric

enum class ChartMode { Bar, Line }

private const val GRID_LINE_COUNT = 3
private const val MAX_ZOOM = 6f
private val TOOLTIP_WIDTH = 132.dp
private val VIEWPORT_HEIGHT = 150.dp
private val Y_AXIS_WIDTH = 34.dp

/** Matches the Canvas's own topInset/axisInset in [UsageChart], so the value labels
 * drawn outside it (fixed, not scrolled by panning) line up with its gridlines. */
private val PLOT_TOP_INSET = 18.dp
private val PLOT_AXIS_INSET = 20.dp

/**
 * Every point gets at least this much width. A crowded chart (24 hourly points) then
 * comes out wider than the screen even before any pinch-zoom, so bars have visible
 * gaps between them instead of touching edge-to-edge — scroll horizontally to see the
 * rest, the same way zooming in already did.
 */
private val MIN_SLOT_WIDTH = 34.dp

/** Bar / line switch, shown next to a chart's headline. */
@Composable
fun ChartModeToggle(mode: ChartMode, onModeChange: (ChartMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(3.dp),
    ) {
        ChartModeButton(Icons.Rounded.BarChart, selected = mode == ChartMode.Bar) { onModeChange(ChartMode.Bar) }
        ChartModeButton(Icons.Rounded.ShowChart, selected = mode == ChartMode.Line) { onModeChange(ChartMode.Line) }
    }
}

@Composable
private fun ChartModeButton(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Usage / sessions switch, shown at the top of a chart card — a single full-width
 * pill split evenly in two, the selected half filled solid, rather than a row of
 * auto-sized chips: with just two options this reads as one control to pick between,
 * not a list to scan.
 */
@Composable
fun MetricSelector(selected: ChartMetric, onSelect: (ChartMetric) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MetricSegment(ChartMetric.USAGE, Icons.Rounded.AccessTime, selected, onSelect, Modifier.weight(1f))
        MetricSegment(ChartMetric.SESSIONS, Icons.Rounded.TouchApp, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun MetricSegment(
    metric: ChartMetric,
    icon: ImageVector,
    selected: ChartMetric,
    onSelect: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = metric == selected
    val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { onSelect(metric) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(metric.chipLabelRes), color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A bar or filled-line chart, pinch-zoomable and pannable in both directions — wider
 * to spread out crowded points (a day's 24 hours), taller to tell close bars apart.
 * A fixed y-axis label column sits to its left, outside the pannable area.
 *
 * The x-axis's hour labels are drawn into the canvas itself rather than laid out as a
 * Row of weighted Text, which gave each hour a fixed 1/24th of the width and clipped
 * "01" down to "0".
 *
 * Bars carry their value above them; the line reveals one on tap instead, since a
 * label per point would be unreadable along a curve.
 */
@Composable
fun UsageChart(
    points: List<ChartPoint>,
    mode: ChartMode,
    formatBarLabel: (Long) -> String,
    formatTooltip: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    // Keyed on points itself, not points.size: switching from one day to another
    // hourly chart keeps the same 24-point count, so a size-only key left an old
    // zoom/scroll carried over onto the new data — the chart opened already scrolled
    // off to wherever the previous day's gesture had left it, with no way back to
    // hour 0 since pan could only move forward from there.
    var zoom by remember(points) { mutableFloatStateOf(1f) }
    // ScrollState instead of a manually offset()'d + clipToBounds() Box: a plain Box
    // centered an oversized requiredWidth() child regardless of contentAlignment (see
    // the debugOversizedChildAlignment screenshot tests), which opened the chart on
    // roughly its middle hour with no way to pan back to hour 0. horizontalScroll /
    // verticalScroll are Compose's own primitive for exactly this — wide content
    // inside a narrow viewport — and position their content from a scroll offset
    // (0 = the content's own start), not from Box alignment, so they sidestep that
    // bug entirely instead of working around it. Driven programmatically (enabled =
    // false, moved only via dispatchRawDelta) since zoom/pan are already handled by
    // the same hand-rolled pinch/drag gesture detectors as before.
    val horizontalScrollState = remember(points) { ScrollState(0) }
    val verticalScrollState = remember(points) { ScrollState(0) }
    var tappedIndex by remember(points, mode) { mutableStateOf<Int?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val fillColor = barColor.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val onPanel = MaterialTheme.colorScheme.onSurface
    val maxValue = (points.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontFamily = MonoNumeric, fontSize = 10.sp, color = onPanel)
    val axisStyle = TextStyle(fontFamily = MonoNumeric, fontSize = 10.sp, color = onPanel.copy(alpha = 0.6f))
    val density = LocalDensity.current

    Row(modifier = modifier.fillMaxWidth()) {
        ChartYAxisLabels(
            maxValue = maxValue,
            formatValue = formatBarLabel,
            style = axisStyle,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(VIEWPORT_HEIGHT)
            // detectTransformGestures consumed every gesture over this area, including
            // a plain vertical one-finger drag -- which meant touching the chart ever
            // blocked the screen's own scroll, not just while pinching. Split it: pinch
            // is handled by hand below and only looks at frames with 2+ pointers down,
            // so a single finger is never consumed here -- it is left for
            // horizontalScroll's own gesture detector below, or the page's own scroll.
            .pointerInput(points.size) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size < 2) continue
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                        // dispatchRawDelta is a plain (non-suspend) function that moves
                        // the scroll offset directly and auto-clamps it to [0, content
                        // size - viewport size], which the ScrollState itself tracks
                        // from the Canvas's actual measured width/height below — no
                        // manual maxPanX/maxPanY bookkeeping needed anymore.
                        horizontalScrollState.dispatchRawDelta(-panChange.x)
                        verticalScrollState.dispatchRawDelta(-panChange.y)
                        pressed.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val baseWidth: Dp = maxOf(maxWidth, MIN_SLOT_WIDTH * points.size)
        val contentWidth: Dp = baseWidth * zoom
        val contentHeight: Dp = maxHeight * zoom

        Box(
            modifier = Modifier
                .fillMaxSize()
                // enabled = true here (unlike verticalScroll below): a hand-rolled
                // detectHorizontalDragGestures + dispatchRawDelta combination did not
                // move the chart at all on a real device -- most likely losing the
                // touch-slop/direction race against the page's own vertical scroll.
                // horizontalScroll's own built-in scrollable() is Compose's
                // battle-tested implementation of exactly that negotiation, so single-
                // finger horizontal panning is handed to it entirely instead of
                // reimplemented by hand. Vertical panning has no such single-finger
                // gesture of its own (it only happens via the pinch loop above), so it
                // stays enabled = false, driven solely by dispatchRawDelta.
                .horizontalScroll(horizontalScrollState, enabled = true)
                .verticalScroll(verticalScrollState, enabled = false),
        ) {
            Canvas(
                // requiredWidth/requiredHeight, not width/height: the latter clamps to
                // the incoming constraints from the scroll container (the viewport),
                // which silently capped the canvas back down to viewport size
                // regardless of contentWidth/Height — defeating both MIN_SLOT_WIDTH and
                // pinch-zoom. required* forces the size past that, which is exactly
                // what content meant to overflow a scrollable viewport needs.
                modifier = Modifier
                    .requiredWidth(contentWidth)
                    .requiredHeight(contentHeight)
                    .pointerInput(points, mode) {
                        if (mode != ChartMode.Line) return@pointerInput
                        detectTapGestures { offset ->
                            if (points.isEmpty()) return@detectTapGestures
                            val slot = size.width.toFloat() / points.size
                            val index = (offset.x / slot).toInt().coerceIn(0, points.size - 1)
                            tappedIndex = if (tappedIndex == index) null else index
                        }
                    },
            ) {
                val topInset = PLOT_TOP_INSET.toPx()
                val axisInset = PLOT_AXIS_INSET.toPx()
                val plotBottom = size.height - axisInset
                val plotHeight = (plotBottom - topInset).coerceAtLeast(1f)

                repeat(GRID_LINE_COUNT) { i ->
                    val y = topInset + plotHeight * i / (GRID_LINE_COUNT - 1)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                when (mode) {
                    ChartMode.Bar -> drawBars(points, maxValue, barColor, plotBottom, plotHeight, measurer, valueStyle, formatBarLabel)
                    ChartMode.Line -> drawLineArea(points, maxValue, barColor, fillColor, topInset, plotBottom, plotHeight)
                }
                drawAxisLabels(points, plotBottom, measurer, axisStyle)
            }
        }

        val index = tappedIndex
        if (index != null && index < points.size) {
            val slot = contentWidth / points.size
            val x = (slot * index - with(density) { horizontalScrollState.value.toDp() })
                .coerceIn(0.dp, (maxWidth - TOOLTIP_WIDTH).coerceAtLeast(0.dp))
            ChartTooltip(points[index], x, formatTooltip)
        }
        }
    }
}

/**
 * Value labels for the chart's y-axis (max, half, zero), drawn outside the pannable
 * viewport so they stay put while the chart itself scrolls horizontally underneath —
 * unlike the per-bar and per-hour labels, which are drawn inside the canvas and pan
 * along with it. [PLOT_TOP_INSET]/[PLOT_AXIS_INSET] mirror the Canvas's own insets so
 * these line up with its gridlines.
 */
@Composable
private fun ChartYAxisLabels(maxValue: Long, formatValue: (Long) -> String, style: TextStyle) {
    Column(
        modifier = Modifier
            .width(Y_AXIS_WIDTH)
            .height(VIEWPORT_HEIGHT)
            .padding(top = PLOT_TOP_INSET, bottom = PLOT_AXIS_INSET, end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        Text(text = formatValue(maxValue), style = style)
        Text(text = formatValue(maxValue / 2), style = style)
        Text(text = formatValue(0L), style = style)
    }
}

private fun DrawScope.drawBars(
    points: List<ChartPoint>,
    maxValue: Long,
    color: Color,
    plotBottom: Float,
    plotHeight: Float,
    measurer: TextMeasurer,
    valueStyle: TextStyle,
    formatBarLabel: (Long) -> String,
) {
    if (points.isEmpty()) return
    val slotWidth = size.width / points.size
    val barWidth = (slotWidth * 0.78f).coerceAtLeast(6.dp.toPx())
    val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
    val labelGap = 3.dp.toPx()
    var lastLabelRight = Float.NEGATIVE_INFINITY

    points.forEachIndexed { index, point ->
        // An hour with no usage gets no bar at all. Drawing a minimum-height stub for
        // it left a row of dashes along the baseline that read as data.
        if (point.value <= 0L) return@forEachIndexed

        val barHeight = (plotHeight * point.value.toFloat() / maxValue).coerceAtLeast(2.dp.toPx())
        val left = slotWidth * index + (slotWidth - barWidth) / 2f
        val top = plotBottom - barHeight
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner,
        )

        // Label every bar that has room. Bars are sparse — most hours are empty — so
        // collisions are rare; where two adjacent bars would overlap, the second label
        // waits until the chart is zoomed in far enough to separate them.
        val label = measurer.measure(formatBarLabel(point.value), valueStyle)
        val labelLeft = left + (barWidth - label.size.width) / 2f
        if (labelLeft > lastLabelRight + labelGap) {
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = labelLeft.coerceIn(0f, size.width - label.size.width),
                    y = (top - label.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
            lastLabelRight = labelLeft + label.size.width
        }
    }
}

private fun DrawScope.drawLineArea(
    points: List<ChartPoint>,
    maxValue: Long,
    lineColor: Color,
    fillColor: Color,
    topInset: Float,
    plotBottom: Float,
    plotHeight: Float,
) {
    if (points.size < 2) return
    val slotWidth = size.width / points.size
    val coordinates = points.mapIndexed { index, point ->
        Offset(
            x = slotWidth * index + slotWidth / 2f,
            y = topInset + plotHeight * (1f - point.value.toFloat() / maxValue),
        )
    }

    val linePath = Path().apply {
        moveTo(coordinates.first().x, coordinates.first().y)
        for (i in 1 until coordinates.size) lineTo(coordinates[i].x, coordinates[i].y)
    }
    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(coordinates.last().x, plotBottom)
        lineTo(coordinates.first().x, plotBottom)
        close()
    }

    drawPath(fillPath, color = fillColor)
    drawPath(
        linePath,
        color = lineColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    coordinates.forEach { drawCircle(color = lineColor, radius = 3.dp.toPx(), center = it) }
}

/** One label per point where they fit, skipping any that would run into the last one drawn. */
private fun DrawScope.drawAxisLabels(
    points: List<ChartPoint>,
    plotBottom: Float,
    measurer: TextMeasurer,
    axisStyle: TextStyle,
) {
    if (points.isEmpty()) return
    val slotWidth = size.width / points.size
    val gap = 4.dp.toPx()
    var lastRight = Float.NEGATIVE_INFINITY

    points.forEachIndexed { index, point ->
        val label = measurer.measure(point.label, axisStyle)
        val left = slotWidth * index + (slotWidth - label.size.width) / 2f
        if (left > lastRight + gap) {
            drawText(
                textLayoutResult = label,
                topLeft = Offset(left.coerceIn(0f, size.width - label.size.width), plotBottom + 5.dp.toPx()),
            )
            lastRight = left + label.size.width
        }
    }
}

@Composable
private fun ChartTooltip(point: ChartPoint, x: Dp, formatTooltip: (Long) -> String) {
    Surface(
        modifier = Modifier
            .padding(top = 4.dp)
            .offset(x = x)
            .width(TOOLTIP_WIDTH),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = point.detailLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
            )
            Text(
                text = formatTooltip(point.value),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoNumeric),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
