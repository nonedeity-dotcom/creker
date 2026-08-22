package com.creker.screentime.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.DurationFormatter
import com.creker.screentime.ui.theme.MonoNumeric

/** A muted, theme-independent green — the one color in this palette that always reads as "good". */
private val ImprovedGreen = Color(0xFF5FB86A)

/** Keeps the card the same height whether or not the period has any data to plot. */
private val EMPTY_CHART_HEIGHT = 96.dp

/**
 * The headline panel: the metric picker, a bar/line toggle, the total for whichever
 * metric is selected, and the chart itself.
 *
 * Shared by the overview screen (all apps) and one app's detail screen — only what
 * surrounds the panel differs between them. Nearly transparent with a thin outline
 * rather than a filled, tinted surface, so it reads as a frame around the chart
 * instead of another block of color competing with the page around it.
 */
@Composable
fun ChartCard(
    metric: ChartMetric,
    onMetricChange: (ChartMetric) -> Unit,
    chartPoints: List<ChartPoint>,
    modifier: Modifier = Modifier,
    /** Total app usage over the period, shown as a row at the bottom of this same card. */
    totalUsageMillis: Long? = null,
    /** False hides the big headline figure, leaving only [subtitle] and the toggle. */
    showHeadlineValue: Boolean = true,
    /** Percent change vs. the previous equally-long period; null hides the chip entirely. */
    usageChangePercent: Int? = null,
    usageChangeIsDecrease: Boolean = true,
    /** True: "чем вчера" wording, for a single-day period. False: "за предыдущий период". */
    usageChangeComparedToYesterday: Boolean = true,
    /** Makes the total-usage row tappable — e.g. to open a full per-app breakdown. */
    onTotalUsageClick: (() -> Unit)? = null,
    subtitle: @Composable () -> Unit = {},
) {
    var mode by remember { mutableStateOf(ChartMode.Bar) }
    val total = chartPoints.sumOf { it.value }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            MetricSelector(selected = metric, onSelect = onMetricChange, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { subtitle() }
                ChartModeToggle(mode = mode, onModeChange = { mode = it })
            }
            if (showHeadlineValue) {
                Text(
                    text = metric.formatValue(total),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (usageChangePercent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    UsageChangeChip(
                        percent = usageChangePercent,
                        isDecrease = usageChangeIsDecrease,
                        comparedToYesterday = usageChangeComparedToYesterday,
                    )
                }
            }

            val units = rememberDurationUnits()
            Spacer(modifier = Modifier.height(12.dp))
            if (chartPoints.any { it.value > 0L }) {
                UsageChart(
                    points = chartPoints,
                    mode = mode,
                    // Bars get the short form: "01:34:36" over a bar is unreadable.
                    formatBarLabel = { metric.formatCompact(it, units) },
                    formatTooltip = metric::formatValue,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                )
            } else {
                // An all-zero chart used to render nothing at all, leaving the card
                // ending in blank space with no way to tell "no usage this period"
                // apart from something being broken — most visible on the screen-time
                // metric, which stays empty until a sync has seen screen on/off events.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EMPTY_CHART_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chart_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (totalUsageMillis != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = onTotalUsageClick != null) { onTotalUsageClick?.invoke() },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.total_usage_row_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DurationFormatter.formatWithUnits(totalUsageMillis, units),
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoNumeric),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (onTotalUsageClick != null) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (!showHeadlineValue && usageChangePercent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    UsageChangeChip(
                        percent = usageChangePercent,
                        isDecrease = usageChangeIsDecrease,
                        comparedToYesterday = usageChangeComparedToYesterday,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageChangeChip(percent: Int, isDecrease: Boolean, comparedToYesterday: Boolean) {
    val tint = if (isDecrease) ImprovedGreen else MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (isDecrease) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                when {
                    isDecrease && comparedToYesterday -> R.string.usage_change_less_today
                    !isDecrease && comparedToYesterday -> R.string.usage_change_more_today
                    isDecrease && !comparedToYesterday -> R.string.usage_change_less_period
                    else -> R.string.usage_change_more_period
                },
                percent,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}
