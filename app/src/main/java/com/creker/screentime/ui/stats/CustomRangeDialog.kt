package com.creker.screentime.ui.stats

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Lets the user pick an arbitrary "from"/"to" range. The Material picker works in UTC
 * milliseconds, so values are converted on both edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangeDialog(
    initialRange: DayRange,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (from: LocalDate, to: LocalDate) -> Unit,
) {
    val todayUtcMillis = remember(today) { today.toUtcMillis() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange.from.toUtcMillis(),
        initialSelectedEndDateMillis = initialRange.to.toUtcMillis(),
        selectableDates = remember(todayUtcMillis) {
            object : SelectableDates {
                // Future days can only ever be empty.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis

                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.toLocalDateUtc() ?: return@TextButton
                    // Tapping a single day leaves the end unselected: treat it as one day.
                    val end = state.selectedEndDateMillis?.toLocalDateUtc() ?: start
                    onConfirm(start, end)
                },
            ) {
                Text(stringResource(R.string.range_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.range_picker_dismiss))
            }
        },
    ) {
        RangePickerBody(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.RangePickerBody(
    state: androidx.compose.material3.DateRangePickerState,
) {
    DateRangePicker(
        state = state,
        modifier = Modifier.weight(1f),
        title = {
            Text(
                text = stringResource(R.string.range_picker_title),
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
            )
        },
    )
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
