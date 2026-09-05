package com.creker.screentime.ui.totaltime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DurationFormatter
import com.creker.screentime.core.StatsPeriod
import com.creker.screentime.core.UsageComparison
import com.creker.screentime.ui.chart.RingSlice
import com.creker.screentime.ui.chart.ringColor
import com.creker.screentime.ui.chart.rememberDurationUnits
import com.creker.screentime.ui.chart.UsageRingChart
import com.creker.screentime.ui.period.PeriodPicker
import com.creker.screentime.ui.stats.CustomRangeDialog
import com.creker.screentime.ui.theme.MonoNumeric
import java.time.LocalDate
import kotlin.math.abs

/**
 * "All apps at once": a ring chart of the period's usage split by app, a card per app
 * with its own change vs. the previous equally-long period, and how much less was used
 * than that previous period overall, when it was in fact less.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalTimeScreen(
    state: TotalTimeUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rangeDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.app_detail_back))
                    }
                },
                title = { Text(stringResource(R.string.total_time_title)) },
            )
        },
    ) { contentPadding ->
        if (state.isInitialLoading) {
            Box(Modifier.padding(contentPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PeriodPicker(
                range = state.range,
                today = today,
                canGoForward = state.canGoForward,
                onPrevious = onPrevious,
                onNext = onNext,
                onToday = { onSelectPeriod(StatsPeriod.Day) },
                onYesterday = { onSelectPeriod(StatsPeriod.Yesterday) },
                onLastWeek = { onSelectPeriod(StatsPeriod.Week) },
                onLastMonth = { onSelectPeriod(StatsPeriod.Month) },
                onCustomRange = { rangeDialogVisible = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                text = stringResource(R.string.ring_chart_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            UsageRingChart(
                slices = ringSlices(state.apps, stringResource(R.string.ring_chart_other)),
                totalLabel = DurationFormatter.format(state.totalMillis),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
            )

            val change = state.totalChangeMillis
            if (change != null) {
                TotalChangeLine(
                    changeMillis = change,
                    singleDay = state.range.dayCount == 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (state.apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.usage_analysis_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                )
                // The list doubles as the ring's legend: the first few rows carry the same
                // colour as their arc, in the same order. Rows rather than a two-column grid
                // of cards — the cards were mostly air, four of them filled half the screen
                // to carry four numbers, and nothing about them said which arc was which.
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.apps.forEachIndexed { index, app ->
                        UsageRow(
                            app = app,
                            // Past the ring's own slices the dot would be a lie: those apps
                            // are inside the single "other" arc, not arcs of their own.
                            dotColor = if (index < MAX_RING_SLICES - 1) ringColor(index) else null,
                            onClick = { onAppClick(app.packageName) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }

    if (rangeDialogVisible) {
        CustomRangeDialog(
            initialRange = state.range,
            today = today,
            onDismiss = { rangeDialogVisible = false },
            onConfirm = { from, to ->
                rangeDialogVisible = false
                onSelectCustomRange(from, to)
            },
        )
    }
}

/**
 * The ring shows the biggest few apps individually and everything else as one slice.
 * A phone with two dozen tracked apps is normal, and drawing a slice per app gave a
 * ring of hairline bands with every icon stacked on top of its neighbours -- the tail
 * carries no readable information at that width, only the fact that it exists.
 */
private fun ringSlices(apps: List<TotalTimeAppUi>, otherLabel: String): List<RingSlice> {
    if (apps.size <= MAX_RING_SLICES) {
        return apps.map { RingSlice(label = it.label, icon = it.icon, share = it.shareOfTotal) }
    }
    val leading = apps.take(MAX_RING_SLICES - 1)
    val otherShare = apps.drop(MAX_RING_SLICES - 1).sumOf { it.shareOfTotal.toDouble() }.toFloat()
    return leading.map { RingSlice(label = it.label, icon = it.icon, share = it.shareOfTotal) } +
        RingSlice(label = otherLabel, icon = null, share = otherShare)
}

/** Including the grouped "other" slice — apps are already sorted by usage, descending. */
private const val MAX_RING_SLICES = 6

/**
 * "Twenty-two minutes less than yesterday", in the same colour as everything else.
 *
 * Deliberately not a card, not green, and not carrying a piggy bank: it is one more fact
 * about the period, the same size as the other facts.
 */
@Composable
private fun TotalChangeLine(changeMillis: Long, singleDay: Boolean, modifier: Modifier = Modifier) {
    val units = rememberDurationUnits()
    val less = changeMillis < 0L
    val res = when {
        less && singleDay -> R.string.total_change_less_yesterday
        less -> R.string.total_change_less
        singleDay -> R.string.total_change_more_yesterday
        else -> R.string.total_change_more
    }
    Text(
        text = stringResource(res, DurationFormatter.formatCompact(abs(changeMillis), units)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** One app: its arc's colour, its icon, its name, its time, and which way it moved. */
@Composable
private fun UsageRow(
    app: TotalTimeAppUi,
    dotColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dotColor ?: Color.Transparent),
        )
        Spacer(modifier = Modifier.width(12.dp))
        AppIconBadge(app)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        UsageChangeRow(app.usageMillis, app.change)
    }
}

@Composable
private fun UsageChangeRow(usageMillis: Long, change: UsageComparison?) {
    val units = rememberDurationUnits()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (change != null) {
            // The arrow says which way; it does not say whether that is good. Green for down
            // and red for up made the list a verdict on every app you opened.
            Icon(
                imageVector = if (change.isDecrease) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(
            text = DurationFormatter.formatCompact(usageMillis, units),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoNumeric),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
