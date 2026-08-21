package com.creker.screentime.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.StatsPeriod

/** Day / week / month / custom switch shown at the top of the statistics screen. */
@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    onPickCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PeriodChip(
            text = stringResource(R.string.period_day),
            selected = selected is StatsPeriod.Day,
            onClick = { onSelect(StatsPeriod.Day) },
        )
        PeriodChip(
            text = stringResource(R.string.period_week),
            selected = selected is StatsPeriod.Week,
            onClick = { onSelect(StatsPeriod.Week) },
        )
        PeriodChip(
            text = stringResource(R.string.period_month),
            selected = selected is StatsPeriod.Month,
            onClick = { onSelect(StatsPeriod.Month) },
        )
        FilterChip(
            selected = selected is StatsPeriod.Custom,
            onClick = onPickCustomRange,
            label = { Text(stringResource(R.string.period_custom)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
    )
}
