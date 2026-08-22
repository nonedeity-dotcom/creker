package com.creker.screentime.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.ui.theme.MonoNumeric
import kotlinx.coroutines.launch

enum class ChartMode { Bar, Line }

private const val GRID_LINE_COUNT = 3
private const val MAX_ZOOM = 4f
private val TOOLTIP_WIDTH = 132.dp
private val PLOT_HEIGHT = 150.dp
private val AXIS_HEIGHT = 18.dp

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
            leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
 * to spread out crowded points (a day's 24 hours), taller to make close bar heights
 * easier to tell apart.
 *
 * Bars carry their value above them; the line reveals one on tap instead, since a
 * label per point would be unreadable along a curve.
 */
@Composable
fun UsageChart(
    points: List<ChartPoint>,
    mode: ChartMode,
    formatValue: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(points.size) { mutableFloatStateOf(1f) }
    var tappedIndex by remember(points.size, mode) { mutableStateOf<Int?>(null) }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        scope.launch {
            horizontalScrollState.scrollBy(-panChange.x)
            verticalScrollState.scrollBy(-panChange.y)
        }
    }

    val barColor = MaterialTheme.colorScheme.primary
    val fillColor = barColor.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.13f)
    val valueColor = MaterialTheme.colorScheme.onPrimaryContainer
    val axisColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
    val maxValue = (points.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(
        fontFamily = MonoNumeric,
        fontSize = 9.sp,
        color = valueColor,
    )

    BoxWithConstraints(
        // The viewport stays put; only the content inside it grows with zoom.
        modifier = modifier
            .fillMaxWidth()
            .height(PLOT_HEIGHT + AXIS_HEIGHT + 6.dp)
            .transformable(transformState),
    ) {
        val contentWidth = maxWidth * zoom
        Box(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState, enabled = false)
                .verticalScroll(verticalScrollState, enabled = false),
        ) {
            Column(Modifier.width(contentWidth)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PLOT_HEIGHT * zoom)
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
                    repeat(GRID_LINE_COUNT) { i ->
                        val y = size.height * i / (GRID_LINE_COUNT - 1)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    when (mode) {
                        ChartMode.Bar -> drawBars(points, maxValue, barColor, measurer, valueStyle, formatValue)
                        ChartMode.Line -> drawLineArea(points, maxValue, barColor, fillColor)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                ChartAxisLabels(points, axisColor)
            }

            val index = tappedIndex
            if (index != null && index < points.size) {
                ChartTooltip(
                    point = points[index],
                    index = index,
                    pointCount = points.size,
                    contentWidth = contentWidth,
                    formatValue = formatValue,
                )
            }
        }
    }
}

private fun DrawScope.drawBars(
    points: List<ChartPoint>,
    maxValue: Long,
    color: Color,
    measurer: TextMeasurer,
    valueStyle: TextStyle,
    formatValue: (Long) -> String,
) {
    if (points.isEmpty()) return
    val slotWidth = size.width / points.size
    val barWidth = (slotWidth * 0.6f).coerceAtLeast(2.dp.toPx())
    // Room above the tallest bar for its own label.
    val labelInset = 16.dp.toPx()
    val plotHeight = (size.height - labelInset).coerceAtLeast(1f)
    val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

    points.forEachIndexed { index, point ->
        // An hour with no usage gets no bar at all. Drawing a minimum-height stub for
        // it left a row of dashes along the baseline that read as data.
        if (point.value <= 0L) return@forEachIndexed

        val barHeight = (plotHeight * point.value.toFloat() / maxValue).coerceAtLeast(2.dp.toPx())
        val left = slotWidth * index + (slotWidth - barWidth) / 2f
        val top = size.height - barHeight
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner,
        )

        val label = measurer.measure(formatValue(point.value), valueStyle)
        // Only label a bar when the slot is actually wide enough to hold the text —
        // otherwise neighbouring labels overlap into noise. Zooming in reveals them.
        if (label.size.width <= slotWidth) {
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = left + (barWidth - label.size.width) / 2f,
                    y = (top - label.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }
    }
}

private fun DrawScope.drawLineArea(points: List<ChartPoint>, maxValue: Long, lineColor: Color, fillColor: Color) {
    if (points.size < 2) return
    val slotWidth = size.width / points.size
    val coordinates = points.mapIndexed { index, point ->
        val x = slotWidth * index + slotWidth / 2f
        val y = size.height * (1f - point.value.toFloat() / maxValue)
        Offset(x, y)
    }

    val linePath = Path().apply {
        moveTo(coordinates.first().x, coordinates.first().y)
        for (i in 1 until coordinates.size) lineTo(coordinates[i].x, coordinates[i].y)
    }
    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(coordinates.last().x, size.height)
        lineTo(coordinates.first().x, size.height)
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

/** One label per point, always — zooming in gives each one the room it needs. */
@Composable
private fun ChartAxisLabels(points: List<ChartPoint>, color: Color) {
    if (points.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { point ->
            Text(
                text = point.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartTooltip(
    point: ChartPoint,
    index: Int,
    pointCount: Int,
    contentWidth: Dp,
    formatValue: (Long) -> String,
) {
    val slotWidth = contentWidth / pointCount
    val x = (slotWidth * index).coerceAtMost((contentWidth - TOOLTIP_WIDTH).coerceAtLeast(0.dp))
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
                text = formatValue(point.value),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoNumeric),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
