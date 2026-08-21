package com.creker.screentime.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.creker.screentime.R
import com.creker.screentime.core.DayRange
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
