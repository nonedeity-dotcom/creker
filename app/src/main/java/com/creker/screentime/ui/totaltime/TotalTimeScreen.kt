package com.creker.screentime.ui.totaltime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Savings
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
import com.creker.screentime.ui.chart.UsageRingChart
import com.creker.screentime.ui.period.PeriodPicker
import com.creker.screentime.ui.stats.CustomRangeDialog
import com.creker.screentime.ui.theme.MonoNumeric
import java.time.LocalDate

/** A muted, theme-independent green — the one color in this palette that always reads as "good". */
private val ImprovedGreen = Color(0xFF5FB86A)

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
                slices = state.apps.map { RingSlice(label = it.label, icon = it.icon, share = it.shareOfTotal) },
                totalLabel = DurationFormatter.format(state.totalMillis),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
            )

            val saved = state.savedMillis
            if (saved != null) {
                TimeSavedCard(saved, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                    modifier = Modifier.padding(horizontal = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.apps.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { app ->
                                UsageAnalysisCard(
                                    app = app,
                                    onClick = { onAppClick(app.packageName) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
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

@Composable
private fun TimeSavedCard(savedMillis: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ImprovedGreen.copy(alpha = 0.14f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Rounded.Savings, contentDescription = null, tint = ImprovedGreen, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.time_saved_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = DurationFormatter.format(savedMillis),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoNumeric),
                color = ImprovedGreen,
            )
        }
    }
}

@Composable
private fun UsageAnalysisCard(app: TotalTimeAppUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppIconBadge(app)
        }
        UsageChangeRow(app.usageMillis, app.change)
    }
}

@Composable
private fun AppIconBadge(app: TotalTimeAppUi) {
    val icon = app.icon
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun UsageChangeRow(usageMillis: Long, change: UsageComparison?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (change != null) {
            val tint = if (change.isDecrease) ImprovedGreen else MaterialTheme.colorScheme.error
            Icon(
                imageVector = if (change.isDecrease) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = DurationFormatter.format(usageMillis),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoNumeric),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
