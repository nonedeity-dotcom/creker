package com.creker.screentime.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.core.ChartMetric

/**
 * The headline panel: the total for whichever metric is selected, a bar/line toggle,
 * the metric picker, and the chart itself.
 *
 * Shared by the overview screen (all apps) and one app's detail screen — only the
 * headline wording and what surrounds the panel differ between them.
 */
@Composable
fun ChartCard(
    metric: ChartMetric,
    onMetricChange: (ChartMetric) -> Unit,
    chartPoints: List<ChartPoint>,
    modifier: Modifier = Modifier,
    headlineLabel: String = stringResource(metric.headlineLabelRes),
    subtitle: @Composable ColumnScope.() -> Unit = {},
) {
    var mode by remember { mutableStateOf(ChartMode.Bar) }
    val total = chartPoints.sumOf { it.value }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A faint bake across the panel, lit at the top like the icon it came from.
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = headlineLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = metric.formatValue(total),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                ChartModeToggle(mode = mode, onModeChange = { mode = it })
            }
            subtitle()
            Spacer(modifier = Modifier.height(14.dp))
            MetricSelector(selected = metric, onSelect = onMetricChange)

            if (chartPoints.any { it.value > 0L }) {
                Spacer(modifier = Modifier.height(18.dp))
                UsageChart(
                    points = chartPoints,
                    mode = mode,
                    formatValue = metric::formatValue,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
