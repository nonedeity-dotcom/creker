package com.creker.screentime.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.core.DayRange

/** The headline number: total screen time over the selected period. */
@Composable
fun TotalTimeCard(
    totalTimeMs: Long,
    range: DayRange,
    appCount: Int,
    modifier: Modifier = Modifier,
) {
    val labels = rememberDurationLabels()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.total_screen_time),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatDuration(totalTimeMs, labels),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = range.formatted(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.apps_count, appCount),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}
