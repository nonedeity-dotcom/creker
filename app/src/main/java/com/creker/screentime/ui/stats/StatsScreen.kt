package com.creker.screentime.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.ChartMetric
import com.creker.screentime.core.StatsPeriod
import com.creker.screentime.ui.period.PeriodPicker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    today: LocalDate,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (String) -> Unit,
    onSelectMetric: (ChartMetric) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rangeDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.export_action),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

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

            UsageOverviewCard(
                metric = state.metric,
                onMetricChange = onSelectMetric,
                range = state.range,
                appCount = state.apps.size,
                chartPoints = state.chartPoints,
                totalMillis = state.totalMillis,
                usageChange = state.usageChange,
                modifier = Modifier.padding(all = 16.dp),
            )

            if (state.hasIncompleteHistory) {
                HistoryNotice(state.earliestStoredDay)
            }

            when {
                state.isInitialLoading -> CenteredMessage(
                    title = stringResource(R.string.loading),
                    showSpinner = true,
                    // weight, not fillMaxSize: inside a Column the latter would ask for
                    // the whole screen height and push the content off the bottom.
                    modifier = Modifier.weight(1f),
                )

                state.apps.isEmpty() -> CenteredMessage(
                    title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.empty_body),
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    Text(
                        text = stringResource(R.string.sorted_by_usage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(items = state.apps, key = { it.packageName }) { app ->
                            AppUsageRow(app, onClick = { onAppClick(app.packageName) })
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
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
private fun HistoryNotice(earliestStoredDay: LocalDate?) {
    if (earliestStoredDay == null) return
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Text(
        text = stringResource(R.string.history_notice, earliestStoredDay.format(formatter)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    showSpinner: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showSpinner) CircularProgressIndicator()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
