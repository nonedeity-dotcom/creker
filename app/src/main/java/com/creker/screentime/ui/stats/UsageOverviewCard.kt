package com.creker.screentime.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.DurationFormatter

private enum class ChartMode { Bar, Line }

/** Points above this count get only 3 axis labels (first / middle / last) instead of one each. */
private const val DENSE_LABEL_THRESHOLD = 8

/**
 * Headline card: total time for the period, and a chart of how it is spread across the
 * day (hourly) or across the period (daily), switchable between a bar and a line view.
 */
@Composable
fun UsageOverviewCard(
    totalMillis: Long,
    range: DayRange,
    appCount: Int,
    chartPoints: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(ChartMode.Bar) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.total_screen_time),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = DurationFormatter.format(totalMillis),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ChartModeToggle(mode = mode, onModeChange = { mode = it })
            }
            Text(
                text = range.formatted(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.apps_count, appCount),
                style = MaterialTheme.typography.bodySmall,
            )

            if (chartPoints.any { it.usageMillis > 0L }) {
                Spacer(modifier = Modifier.height(20.dp))
                UsageChart(
                    points = chartPoints,
                    mode = mode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartModeToggle(mode: ChartMode, onModeChange: (ChartMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(2.dp),
    ) {
        ChartModeButton(Icons.Rounded.BarChart, selected = mode == ChartMode.Bar) {
            onModeChange(ChartMode.Bar)
        }
        ChartModeButton(Icons.Rounded.ShowChart, selected = mode == ChartMode.Line) {
            onModeChange(ChartMode.Line)
        }
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

@Composable
private fun UsageChart(points: List<ChartPoint>, mode: ChartMode, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val fillColor = barColor.copy(alpha = 0.20f)
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val maxValue = (points.maxOfOrNull { it.usageMillis } ?: 0L).coerceAtLeast(1L)

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
        Spacer(modifier = Modifier.height(6.dp))
        ChartAxisLabels(points)
    }
}

private fun DrawScope.drawBars(points: List<ChartPoint>, maxValue: Long, color: Color) {
    if (points.isEmpty()) return
    val slotWidth = size.width / points.size
    val barWidth = (slotWidth * 0.55f).coerceAtLeast(2.dp.toPx())
    points.forEachIndexed { index, point ->
        val barHeight = (size.height * point.usageMillis.toFloat() / maxValue).coerceAtLeast(2.dp.toPx())
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
        val y = size.height * (1f - point.usageMillis.toFloat() / maxValue)
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
private fun ChartAxisLabels(points: List<ChartPoint>) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)

    if (points.isEmpty()) return

    if (points.size <= DENSE_LABEL_THRESHOLD) {
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = style,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = points.first().label, style = style, color = color)
            Text(text = points[points.size / 2].label, style = style, color = color)
            Text(text = points.last().label, style = style, color = color)
        }
    }
}

private const val GRID_LINE_COUNT = 3
