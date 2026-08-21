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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import kotlinx.coroutines.launch

enum class ChartMode { Bar, Line }

private const val DENSE_LABEL_THRESHOLD = 8
private const val GRID_LINE_COUNT = 3
private const val MAX_ZOOM = 4f
private val TOOLTIP_WIDTH = 132.dp

/** Bar / line switch, shown next to a chart's headline. */
@Composable
fun ChartModeToggle(mode: ChartMode, onModeChange: (ChartMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(2.dp),
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
    FilterChip(
        selected = metric == selected,
        onClick = { onSelect(metric) },
        label = { Text(stringResource(metric.chipLabelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/**
 * A bar or filled-line chart with pinch-to-zoom and horizontal panning, plus, per the
 * mode: bar mode always labels every bar's value; line mode reveals a value on tap
 * instead, since a label per point would be unreadable on a smooth curve.
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
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        scope.launch { scrollState.scrollBy(-panChange.x) }
    }

    val barColor = MaterialTheme.colorScheme.primary
    val fillColor = barColor.copy(alpha = 0.20f)
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val maxValue = (points.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    BoxWithConstraints(modifier = modifier.transformable(transformState)) {
        val contentWidth = maxWidth * zoom
        Box(modifier = Modifier.horizontalScroll(scrollState, enabled = false)) {
            Column(Modifier.width(contentWidth)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
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
                        ChartMode.Bar -> drawBars(points, maxValue, barColor)
                        ChartMode.Line -> drawLineArea(points, maxValue, barColor, fillColor)
                    }
                }
                if (mode == ChartMode.Bar) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BarValueLabels(points, formatValue)
                }
                Spacer(modifier = Modifier.height(6.dp))
                ChartAxisLabels(points)
            }

            val index = tappedIndex
            if (index != null && index < points.size) {
                ChartTooltip(point = points[index], index = index, pointCount = points.size, contentWidth = contentWidth, formatValue = formatValue)
            }
        }
    }
}

private fun DrawScope.drawBars(points: List<ChartPoint>, maxValue: Long, color: Color) {
    if (points.isEmpty()) return
    val slotWidth = size.width / points.size
    val barWidth = (slotWidth * 0.55f).coerceAtLeast(2.dp.toPx())
    points.forEachIndexed { index, point ->
        val barHeight = (size.height * point.value.toFloat() / maxValue).coerceAtLeast(2.dp.toPx())
        val left = slotWidth * index + (slotWidth - barWidth) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
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

@Composable
private fun BarValueLabels(points: List<ChartPoint>, formatValue: (Long) -> String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { point ->
            Text(
                text = if (point.value > 0L) formatValue(point.value) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartAxisLabels(points: List<ChartPoint>) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)

    if (points.isEmpty()) return

    if (points.size <= DENSE_LABEL_THRESHOLD) {
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(text = point.label, style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = points.first().label, style = style, color = color)
            Text(text = points[points.size / 2].label, style = style, color = color)
            Text(text = points.last().label, style = style, color = color)
        }
    }
}

@Composable
private fun ChartTooltip(point: ChartPoint, index: Int, pointCount: Int, contentWidth: Dp, formatValue: (Long) -> String) {
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
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
            )
            Text(
                text = formatValue(point.value),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
