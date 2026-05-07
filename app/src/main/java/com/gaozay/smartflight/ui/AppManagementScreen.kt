package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.status
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppListStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementScreen(
    state: AppsUiState,
    innerPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onRefreshApps: () -> Unit,
    onSetListStatus: (String, AppListStatus) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "应用列表",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.lastScanSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onRefreshApps,
                        enabled = !state.isScanning,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (state.isScanning) "扫描中" else "扫描")
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索应用名称或包名") },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { filter ->
                    ElevatedFilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text("${filter.label} ${state.countFor(filter)}") },
                    )
                }
            }
        }
        if (state.apps.isEmpty()) {
            item {
                EmptyAppsCard(onRefreshApps = onRefreshApps)
            }
        } else {
            items(
                items = state.apps,
                key = { it.packageName },
            ) { app ->
                AppRow(
                    app = app,
                    onSetListStatus = onSetListStatus,
                )
            }
        }
    }
}

@Composable
private fun EmptyAppsCard(onRefreshApps: () -> Unit) {
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
                text = "暂无应用可显示",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "点击扫描读取已安装应用，并自动识别可能需要联网控制的候选应用。",
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
private fun AppRow(
    app: InstalledAppEntity,
    onSetListStatus: (String, AppListStatus) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                AssistChip(
                    onClick = {},
                    label = { Text(app.status().label) },
                )
            }
            Text(
                text = buildString {
                    append(if (app.declaresInternetPermission) "声明联网权限" else "未声明联网权限")
                    append(" · ")
                    append(if (app.hasLauncherEntry) "有启动入口" else "无启动入口")
                    append(" · ")
                    append(if (app.isSystemApp) "系统应用" else "用户应用")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusButton(
                    label = "白名单",
                    selected = app.status() == AppListStatus.Whitelist,
                    onClick = { onSetListStatus(app.packageName, AppListStatus.Whitelist) },
                )
                StatusButton(
                    label = "黑名单",
                    selected = app.status() == AppListStatus.Blacklist,
                    onClick = { onSetListStatus(app.packageName, AppListStatus.Blacklist) },
                )
                StatusButton(
                    label = "忽略",
                    selected = app.status() == AppListStatus.Ignored,
                    onClick = { onSetListStatus(app.packageName, AppListStatus.Ignored) },
                )
                StatusButton(
                    label = "候选",
                    selected = app.status() == AppListStatus.Candidate,
                    onClick = { onSetListStatus(app.packageName, AppListStatus.Candidate) },
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}
