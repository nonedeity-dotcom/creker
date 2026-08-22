package com.creker.screentime.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DayRange
import com.creker.screentime.ui.chart.ChartCard
import com.creker.screentime.ui.chart.ChartPoint

/** The overview screen's headline card: a chart, its metric and mode, period and app count. */
@Composable
fun UsageOverviewCard(
    metric: ChartMetric,
    onMetricChange: (ChartMetric) -> Unit,
    range: DayRange,
    appCount: Int,
    chartPoints: List<ChartPoint>,
    totalMillis: Long,
    usageChange: UsageComparison?,
    modifier: Modifier = Modifier,
) {
    ChartCard(
        metric = metric,
        onMetricChange = onMetricChange,
        chartPoints = chartPoints,
        modifier = modifier,
        showHeadlineValue = false,
        totalUsageMillis = if (appCount > 0) totalMillis else null,
        usageChangePercent = usageChange?.percent,
        usageChangeIsDecrease = usageChange?.isDecrease ?: true,
        usageChangeComparedToYesterday = usageChange?.comparedToYesterday ?: true,
        subtitle = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = range.formatted(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 6.dp),
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                )

                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.apps_count, appCount),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
    )
}
