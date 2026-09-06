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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric


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
    /** False hides the big headline figure, leaving only [subtitle] and the toggle. */
    showHeadlineValue: Boolean = true,
    /**
     * The headline figure, already formatted. Defaults to the chart's own sum, which is
     * what one app's detail screen wants. The overview passes the total its app list adds
     * up to instead: that list is the body of the screen, and a headline computed from the
     * chart could disagree with it by a rounding step.
     */
    headlineText: String? = null,
    /** A small caption over the headline, saying what the figure is. */
    headlineLabel: String? = null,
    /** Makes the headline tappable — e.g. to open a full per-app breakdown. */
    onHeadlineClick: (() -> Unit)? = null,
    /** Percent change vs. the previous equally-long period; null hides the chip entirely. */
    usageChangePercent: Int? = null,
    usageChangeIsDecrease: Boolean = true,
    /** True: "чем вчера" wording, for a single-day period. False: "за предыдущий период". */
    usageChangeComparedToYesterday: Boolean = true,
    subtitle: @Composable () -> Unit = {},
) {
    var mode by remember { mutableStateOf(ChartMode.Bar) }
    val total = chartPoints.sumOf { it.value }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        // A card, not a patch of background with a hairline round it. On the old palette
        // surface and background were the same colour, so this "card" was invisible except
        // for its border.
        color = MaterialTheme.colorScheme.surfaceContainer,
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
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = onHeadlineClick != null) { onHeadlineClick?.invoke() },
                ) {
                    if (headlineLabel != null) {
                        Text(
                            text = headlineLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = headlineText ?: metric.formatValue(total),
                            style = MaterialTheme.typography.displayMedium,
                        )
                        if (onHeadlineClick != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
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
                    formatAxisTick = { value, max -> metric.formatAxisTick(value, max, units) },
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
        }
    }
}

/**
 * "18% less than yesterday", as a fact rather than a grade.
 *
 * It used to be green when the number fell and error-red when it rose — the last place in
 * the app still handing out verdicts after the "time saved" card and the coloured arrows in
 * the app list went. The arrow still says which way; nothing says whether that is good.
 */
@Composable
private fun UsageChangeChip(percent: Int, isDecrease: Boolean, comparedToYesterday: Boolean) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
