package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.gaozay.smartflight.activities.ActivityRuleSummary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.isOnline
import com.gaozay.smartflight.apps.sourceTag
import com.gaozay.smartflight.apps.statusLabel
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag

@Composable
internal fun EmptyAppsCard(onRefreshApps: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.no_app_data_yet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.apps_scan_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRefreshApps) {
                Text(stringResource(R.string.scan_installed_apps))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppRow(
    app: InstalledAppEntity,
    onSetManualOnline: (String) -> Unit,
    onSetManualOffline: (String) -> Unit,
    onResetToDefault: (String) -> Unit,
    onOpenDetails: (String) -> Unit = {},
    activitySummary: ActivityRuleSummary? = null,
) {
    val sourceTag = app.sourceTag()
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    Card(
        onClick = { onOpenDetails(app.packageName) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = app.packageName, label = app.label, app = app)
            Spacer(modifier = Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoTag(
                        text = stringResource(if (app.isOnline()) R.string.activity_default_online else R.string.activity_default_offline),
                        containerColor = app.statusTagContainerColor(),
                        contentColor = app.statusTagContentColor(),
                    )
                    activitySummary?.takeIf { it.ruleCount > 0 }?.let {
                        InfoTag(stringResource(if (it.rulesEnabled) R.string.activity_rule_count else R.string.activity_rule_count_paused, it.ruleCount))
                        if (it.validCount < it.ruleCount) InfoTag(stringResource(R.string.activity_invalid_count, it.ruleCount - it.validCount))
                    }
                    sourceTag?.let {
                        InfoTag(
                            text = it.label,
                            containerColor = it.tagContainerColor(),
                            contentColor = it.tagContentColor(),
                        )
                    }
                    InfoTag(text = if (app.declaresInternetPermission) stringResource(R.string.declares_internet_permission) else stringResource(R.string.no_internet_permission_declared))
                    InfoTag(text = if (app.isSystemApp) stringResource(R.string.system_apps) else stringResource(R.string.user_apps))
                }
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.change_rule))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.current, app.statusLabel(), sourceTag?.let { " · ${it.label}" } ?: "")) },
                        enabled = false,
                        onClick = {},
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.set_as_online)) },
                        onClick = {
                            expanded = false
                            onSetManualOnline(app.packageName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.set_as_offline)) },
                        onClick = {
                            expanded = false
                            onSetManualOffline(app.packageName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.restore_default)) },
                        onClick = {
                            expanded = false
                            onResetToDefault(app.packageName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTag(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstalledAppEntity.statusTagContainerColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.errorContainer
    isOnline() -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun InstalledAppEntity.statusTagContentColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.onErrorContainer
    isOnline() -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun AppOnlineSourceTag.tagContainerColor(): Color = when (this) {
    AppOnlineSourceTag.Auto -> MaterialTheme.colorScheme.secondaryContainer
    AppOnlineSourceTag.Manual -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun AppOnlineSourceTag.tagContentColor(): Color = when (this) {
    AppOnlineSourceTag.Auto -> MaterialTheme.colorScheme.onSecondaryContainer
    AppOnlineSourceTag.Manual -> MaterialTheme.colorScheme.onTertiaryContainer
}
