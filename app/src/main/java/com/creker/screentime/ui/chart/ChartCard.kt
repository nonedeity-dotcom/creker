package com.creker.screentime.ui.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.core.ChartMetric

/**
 * A headline value for the selected metric, a bar/line toggle, a metric picker, and
 * the chart itself. Shared by the overview screen (all apps) and one app's detail
 * screen — only what surrounds this card differs between them.
 */
@Composable
fun ChartCard(
    metric: ChartMetric,
    onMetricChange: (ChartMetric) -> Unit,
    chartPoints: List<ChartPoint>,
    modifier: Modifier = Modifier,
    subtitle: @Composable ColumnScope.() -> Unit = {},
) {
    var mode by remember { mutableStateOf(ChartMode.Bar) }
    val total = chartPoints.sumOf { it.value }

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
                    Text(text = stringResource(metric.headlineLabelRes), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = metric.formatValue(total),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ChartModeToggle(mode = mode, onModeChange = { mode = it })
            }
            subtitle()
            Spacer(modifier = Modifier.height(12.dp))
            MetricSelector(selected = metric, onSelect = onMetricChange)

            if (chartPoints.any { it.value > 0L }) {
                Spacer(modifier = Modifier.height(20.dp))
                UsageChart(
                    points = chartPoints,
                    mode = mode,
                    formatValue = metric::formatValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }
        }
    }
}
