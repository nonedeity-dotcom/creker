package com.creker.screentime.ui.period

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import com.creker.screentime.ui.stats.formatted
import java.time.LocalDate

/**
 * One button replacing a whole row of period controls: a pill showing the current
 * range (labelled "Сегодня"/"Вчера" for those two specific days), stepping arrows on
 * either side, and — on tap — a menu of presets plus a custom range. Shared by the
 * overview screen and every app's detail screen so period selection looks and works
 * the same everywhere.
 */
@Composable
fun PeriodPicker(
    range: DayRange,
    today: LocalDate,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onLastWeek: () -> Unit,
    onLastMonth: () -> Unit,
    onCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(imageVector = Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.app_detail_previous_period))
        }
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { menuExpanded = true }
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
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_detail_today)) },
                    onClick = { menuExpanded = false; onToday() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_detail_yesterday)) },
                    onClick = { menuExpanded = false; onYesterday() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.period_preset_last_week)) },
                    onClick = { menuExpanded = false; onLastWeek() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.period_preset_last_month)) },
                    onClick = { menuExpanded = false; onLastMonth() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.period_preset_custom)) },
                    onClick = { menuExpanded = false; onCustomRange() },
                )
            }
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.app_detail_next_period))
        }
    }
}

@Composable
private fun periodLabel(range: DayRange, today: LocalDate): String = when (range) {
    DayRange(today, today) -> stringResource(R.string.app_detail_today)
    DayRange(today.minusDays(1), today.minusDays(1)) -> stringResource(R.string.app_detail_yesterday)
    else -> range.formatted()
}
