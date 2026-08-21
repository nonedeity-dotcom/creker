package com.creker.screentime.ui.appdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import com.creker.screentime.core.DurationFormatter
import com.creker.screentime.ui.stats.formatted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One app's stats: usage, sessions, streaks and peak day, in a tile grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    state: AppDetailUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.app_detail_back),
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = state.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(imageVector = Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(text = state.label)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PeriodStepper(
                range = state.range,
                today = today,
                canGoForward = state.canGoForward,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier.padding(16.dp),
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else {
                StatGrid(state)
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PeriodStepper(
    range: DayRange,
    today: LocalDate,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.app_detail_previous_period),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = periodLabel(range, today), color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.app_detail_next_period),
            )
        }
    }
}

@Composable
private fun periodLabel(range: DayRange, today: LocalDate): String = when (range) {
    DayRange(today, today) -> stringResource(R.string.app_detail_today)
    DayRange(today.minusDays(1), today.minusDays(1)) -> stringResource(R.string.app_detail_yesterday)
    else -> range.formatted()
}

@Composable
private fun StatGrid(state: AppDetailUiState) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatRow {
            StatTile(
                label = stringResource(R.string.app_detail_usage_label),
                value = DurationFormatter.format(state.usageMillis),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.app_detail_sessions_label),
                value = state.launchCount.toString(),
                accent = true,
                modifier = Modifier.weight(1f),
            )
        }
        StatRow {
            StatTile(
                label = stringResource(R.string.app_detail_average_label),
                value = DurationFormatter.format(state.averagePerDayMillis),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.app_detail_current_streak_label),
                value = stringResource(R.string.app_detail_days_format, state.currentStreakDays),
                modifier = Modifier.weight(1f),
            )
        }
        StatRow {
            StatTile(
                label = stringResource(R.string.app_detail_longest_streak_label),
                value = stringResource(R.string.app_detail_days_format, state.longestStreakDays),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.app_detail_max_day_label),
                value = DurationFormatter.format(state.maxDayMillis),
                modifier = Modifier.weight(1f),
            )
        }
        StatRow {
            StatTile(
                label = stringResource(R.string.app_detail_install_date_label),
                value = formatInstallDate(state.installedAtMs),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatInstallDate(installedAtMs: Long?): String {
    if (installedAtMs == null) return "—"
    val date = Instant.ofEpochMilli(installedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(INSTALL_DATE_FORMATTER)
}

private val INSTALL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
