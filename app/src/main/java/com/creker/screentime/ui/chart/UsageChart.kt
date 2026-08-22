package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.ui.theme.MonoNumeric
import kotlin.math.roundToInt

enum class ChartMode { Bar, Line }

private const val GRID_LINE_COUNT = 3
private const val MAX_ZOOM = 6f
private val TOOLTIP_WIDTH = 132.dp
private val VIEWPORT_HEIGHT = 190.dp

/** Bar / line switch, shown next to a chart's headline. */
@Composable
fun ChartModeToggle(mode: ChartMode, onModeChange: (ChartMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .padding(3.dp),
    ) {
        ChartModeButton(Icons.Rounded.BarChart, selected = mode == ChartMode.Bar) { onModeChange(ChartMode.Bar) }
        ChartModeButton(Icons.Rounded.ShowChart, selected = mode == ChartMode.Line) { onModeChange(ChartMode.Line) }
    }
}

@Composable
private fun ChartModeButton(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
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

/** Usage / sessions / screen-time switch, shown below a chart's headline. */
@Composable
fun MetricSelector(selected: ChartMetric, onSelect: (ChartMetric) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricChip(ChartMetric.USAGE, Icons.Rounded.AccessTime, selected, onSelect)
        MetricChip(ChartMetric.SESSIONS, Icons.Rounded.TouchApp, selected, onSelect)
        MetricChip(ChartMetric.SCREEN_TIME, Icons.Rounded.Smartphone, selected, onSelect)
    }
}

@Composable
private fun MetricChip(metric: ChartMetric, icon: ImageVector, selected: ChartMetric, onSelect: (ChartMetric) -> Unit) {
    val isSelected = metric == selected
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(metric) },
        label = { Text(stringResource(metric.chipLabelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        // The chips sit on the warm headline panel, so they take their colours from it
        // rather than from the default surface palette, which washed out against it.
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
            selectedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * A bar or filled-line chart, pinch-zoomable and pannable in both directions — wider
 * to spread out crowded points (a day's 24 hours), taller to tell close bars apart.
 *
 * Everything is drawn into one canvas, axis labels included. Laying them out as a Row
 * of weighted Text instead gave each hour a fixed 1/24th of the width, which clipped
 * "01" down to "0" and ran the labels together.
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
    var zoom by remember(points.size) { mutableFloatStateOf(1f) }
    var pan by remember(points.size) { mutableStateOf(Offset.Zero) }
    var tappedIndex by remember(points.size, mode) { mutableStateOf<Int?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val fillColor = barColor.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.13f)
    val onPanel = MaterialTheme.colorScheme.onPrimaryContainer
    val maxValue = (points.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(fontFamily = MonoNumeric, fontSize = 10.sp, color = onPanel)
    val axisStyle = TextStyle(fontFamily = MonoNumeric, fontSize = 10.sp, color = onPanel.copy(alpha = 0.6f))
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(VIEWPORT_HEIGHT)
            .clipToBounds()
            // detectTransformGestures consumes the pointers it uses, so a pinch here
            // is not stolen by the scrolling screen this chart sits on. Panning only
            // engages once zoomed in, leaving one-finger drags to scroll the page.
            .pointerInput(points.size) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                    val maxPanX = size.width * (newZoom - 1f)
                    val maxPanY = size.height * (newZoom - 1f)
                    zoom = newZoom
                    pan = if (newZoom > 1f) {
                        Offset(
                            (pan.x - panChange.x).coerceIn(0f, maxPanX),
                            (pan.y - panChange.y).coerceIn(0f, maxPanY),
                        )
                    } else {
                        Offset.Zero
                    }
                }
            },
    ) {
        val contentWidth: Dp = maxWidth * zoom
        val contentHeight: Dp = maxHeight * zoom
        Canvas(
            modifier = Modifier
                .width(contentWidth)
                .height(contentHeight)
                .offset { IntOffset(-pan.x.roundToInt(), -pan.y.roundToInt()) }
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
            val topInset = 18.dp.toPx()
            val axisInset = 20.dp.toPx()
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

        val index = tappedIndex
        if (index != null && index < points.size) {
            val slot = contentWidth / points.size
            val x = (slot * index - with(density) { pan.x.toDp() })
                .coerceIn(0.dp, (maxWidth - TOOLTIP_WIDTH).coerceAtLeast(0.dp))
            ChartTooltip(points[index], x, formatTooltip)
        }
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
    val barWidth = (slotWidth * 0.72f).coerceAtLeast(4.dp.toPx())
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
