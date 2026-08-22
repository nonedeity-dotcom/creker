package com.creker.screentime.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.ui.chart.ChartCard
import com.creker.screentime.ui.chart.ChartPoint

/** The overview screen's headline card: total for the period, a chart, its metric and mode. */
@Composable
fun UsageOverviewCard(
    metric: ChartMetric,
    onMetricChange: (ChartMetric) -> Unit,
    range: DayRange,
    appCount: Int,
    chartPoints: List<ChartPoint>,
    totalMillis: Long,
    modifier: Modifier = Modifier,
) {
    ChartCard(
        metric = metric,
        onMetricChange = onMetricChange,
        chartPoints = chartPoints,
        modifier = modifier,
        totalUsageMillis = if (appCount > 0) totalMillis else null,
        subtitle = {
            Text(
                text = range.formatted(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.apps_count, appCount),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
