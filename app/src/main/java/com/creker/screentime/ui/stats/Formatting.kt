package com.creker.screentime.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun DayRange.formatted(): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val fromText = from.format(formatter)
    val toText = to.format(formatter)
    return if (from == to) {
        stringResource(R.string.range_single_day, fromText)
    } else {
        stringResource(R.string.range_days, fromText, toText)
    }
}

/**
 * A short "day month" range for tight spaces — the period picker button, where the
 * full localized date ("16 авг. 2026 г. — 22 авг. 2026 г.") wrapped onto a second
 * line. Drops the year unless the range actually crosses one.
 */
@Composable
fun DayRange.formattedCompact(): String {
    val pattern = if (from.year == to.year) "d MMM" else "d MMM yy"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }
    val fromText = from.format(formatter)
    return if (from == to) {
        stringResource(R.string.range_single_day, fromText)
    } else {
        stringResource(R.string.range_days, fromText, to.format(formatter))
    }
}
