package com.gaozay.smartflight.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                text = "还没有应用数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击扫描读取已安装应用，并自动识别需要联网的应用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRefreshApps) {
                Text("扫描已安装应用")
            }
        }
    }
}

@Composable
internal fun AppRow(
    app: InstalledAppEntity,
    onSetManualOnline: (String) -> Unit,
    onSetManualOffline: (String) -> Unit,
    onResetToDefault: (String) -> Unit,
) {
    val sourceTag = app.sourceTag()
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    Card(
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoTag(
                        text = app.statusLabel(),
                        containerColor = app.statusTagContainerColor(),
                        contentColor = app.statusTagContentColor(),
                    )
                    sourceTag?.let {
                        InfoTag(
                            text = it.label,
                            containerColor = it.tagContainerColor(),
                            contentColor = it.tagContentColor(),
                        )
                    }
                    InfoTag(text = if (app.declaresInternetPermission) "声明联网" else "未声明联网")
                    InfoTag(text = if (app.isSystemApp) "系统应用" else "用户应用")
                }
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更改规则")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("当前：${app.statusLabel()}${sourceTag?.let { " · ${it.label}" } ?: ""}") },
                        enabled = false,
                        onClick = {},
                    )
                    DropdownMenuItem(
                        text = { Text("设为联网") },
                        onClick = {
                            expanded = false
                            onSetManualOnline(app.packageName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("设为非联网") },
                        onClick = {
                            expanded = false
                            onSetManualOffline(app.packageName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("恢复默认") },
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
