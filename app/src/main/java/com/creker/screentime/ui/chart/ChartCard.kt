package com.creker.screentime.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DurationFormatter
import com.creker.screentime.ui.theme.MonoNumeric

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
    /** Total app usage over the period, shown as a row at the bottom of this same card. */
    totalUsageMillis: Long? = null,
    /** False hides the big headline figure, leaving only the label and [subtitle]. */
    showHeadlineValue: Boolean = true,
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
                    if (showHeadlineValue) {
                        Text(
                            text = metric.formatValue(total),
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                ChartModeToggle(mode = mode, onModeChange = { mode = it })
            }
            subtitle()
            Spacer(modifier = Modifier.height(14.dp))
            MetricSelector(selected = metric, onSelect = onMetricChange)

            val units = rememberDurationUnits()
            if (chartPoints.any { it.value > 0L }) {
                Spacer(modifier = Modifier.height(18.dp))
                UsageChart(
                    points = chartPoints,
                    mode = mode,
                    // Bars get the short form: "01:34:36" over a bar is unreadable.
                    formatBarLabel = { metric.formatCompact(it, units) },
                    formatTooltip = metric::formatValue,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                )
            }

            if (totalUsageMillis != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.total_usage_row_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DurationFormatter.formatWithUnits(totalUsageMillis, units),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoNumeric),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
