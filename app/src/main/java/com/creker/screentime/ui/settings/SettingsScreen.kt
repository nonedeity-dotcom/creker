package com.creker.screentime.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creker.screentime.R
import com.creker.screentime.data.settings.CallerAccess
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The same muted green the total-time screen uses for "this is the good state". */
private val GrantedGreen = Color(0xFF5FB86A)

/**
 * Everything that is a setting rather than a statistic, on one screen behind the gear.
 *
 * Three things live here, in the order they matter if something looks wrong:
 * whether Android is actually letting creker measure anything, whether creker answers
 * other apps on this phone, and the CSV backup pair (which used to sit in the overview's
 * top bar, where two rarely-used icons crowded the one that gets tapped daily).
 *
 * Stateless on purpose — the caller owns the switch and the permission state — so the
 * screenshot test can render every combination without a device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasUsageAccess: Boolean,
    callers: List<CallerUi>,
    onBack: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onCallerAllowedChange: (String, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UsageAccessSection(
                hasUsageAccess = hasUsageAccess,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            )
            SharingSection(callers = callers, onCallerAllowedChange = onCallerAllowedChange)
            BackupSection(onExport = onExport, onImport = onImport)
        }
    }
}

@Composable
private fun UsageAccessSection(hasUsageAccess: Boolean, onOpenUsageAccessSettings: () -> Unit) {
    SettingsCard(title = stringResource(R.string.settings_access_section)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasUsageAccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (hasUsageAccess) GrantedGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (hasUsageAccess) R.string.settings_access_granted else R.string.settings_access_denied,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Body(
            stringResource(
                if (hasUsageAccess) R.string.settings_access_granted_body else R.string.settings_access_denied_body,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Offered even when access is already granted: this is also the way back to the
        // system screen to check or revoke it, which is otherwise buried several levels
        // deep in Android's own settings.
        OutlinedButton(onClick = onOpenUsageAccessSettings) {
            Text(stringResource(R.string.settings_access_action))
        }
    }
}

@Composable
private fun SharingSection(callers: List<CallerUi>, onCallerAllowedChange: (String, Boolean) -> Unit) {
    SettingsCard(title = stringResource(R.string.settings_sharing_section)) {
        Body(stringResource(R.string.settings_sharing_body))
        Spacer(modifier = Modifier.height(4.dp))
        callers.forEachIndexed { index, caller ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            CallerRow(caller = caller, onAllowedChange = { onCallerAllowedChange(caller.packageName, it) })
        }
    }
}

@Composable
private fun CallerRow(caller: CallerUi, onAllowedChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caller.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = caller.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        !caller.isInstalled -> stringResource(R.string.settings_caller_not_installed)
                        caller.lastSeenMs <= 0L -> stringResource(R.string.settings_caller_never)
                        else -> stringResource(R.string.settings_caller_last_seen, formatLastSeen(caller.lastSeenMs))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = caller.allowed, onCheckedChange = onAllowedChange)
        }
        // Turning the companion off silently stops it working, so this one consequence is
        // spelled out where it happens rather than left to be discovered in the other app.
        if (caller.packageName == CallerAccess.NO_BURNOUT_PACKAGE && !caller.allowed) {
            Text(
                text = stringResource(R.string.settings_caller_noburnout_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Date and time of the last query, in whatever short form the phone's locale uses. */
@Composable
private fun formatLastSeen(epochMillis: Long): String =
    remember(epochMillis) {
        LAST_SEEN_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }

private val LAST_SEEN_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)

@Composable
private fun BackupSection(onExport: () -> Unit, onImport: () -> Unit) {
    SettingsCard(title = stringResource(R.string.settings_backup_section)) {
        Body(stringResource(R.string.settings_backup_body))
        Spacer(modifier = Modifier.height(12.dp))
        // Stacked rather than side by side: at half the card's width the Russian labels
        // wrapped onto two lines inside the buttons ("Сохранить / данные"), which the
        // screenshot test caught.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_action))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_action))
            }
        }
    }
}

/** The app's card idiom — a bordered surface with 22.dp corners — with a section title on top. */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
