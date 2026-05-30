package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter

@Composable
internal fun FilterSummaryRow(
    state: AppsUiState,
    onFilterChange: (AppFilter) -> Unit,
    onInternetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    onAppTypeFilterChange: (AppTypeFilter) -> Unit,
    onLauncherFilterChange: (LauncherFilter) -> Unit,
    onClearAdvancedFilters: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前显示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${state.filteredCount} / ${state.totalCount} · ${state.filter.zhLabel()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                StatusFilterMenu(state = state, onFilterChange = onFilterChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdvancedFilterMenu(
                    label = "联网",
                    value = state.internetPermissionFilter.zhLabel(),
                    active = state.internetPermissionFilter != InternetPermissionFilter.All,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    InternetPermissionFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.zhLabel()) },
                            onClick = {
                                dismiss()
                                onInternetPermissionFilterChange(filter)
                            },
                        )
                    }
                }
                AdvancedFilterMenu(
                    label = "类型",
                    value = state.appTypeFilter.zhLabel(),
                    active = state.appTypeFilter != AppTypeFilter.User,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    AppTypeFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.zhLabel()) },
                            onClick = {
                                dismiss()
                                onAppTypeFilterChange(filter)
                            },
                        )
                    }
                }
                AdvancedFilterMenu(
                    label = "入口",
                    value = state.launcherFilter.zhLabel(),
                    active = state.launcherFilter != LauncherFilter.All,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    LauncherFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.zhLabel()) },
                            onClick = {
                                dismiss()
                                onLauncherFilterChange(filter)
                            },
                        )
                    }
                }
            }
            if (state.activeAdvancedFilterCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已启用 ${state.activeAdvancedFilterCount} 个高级条件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onClearAdvancedFilters) {
                        Text("清除")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterMenu(
    state: AppsUiState,
    onFilterChange: (AppFilter) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("范围")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text("${filter.zhLabel()} ${state.countFor(filter)}") },
                    onClick = {
                        expanded = false
                        onFilterChange(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun AdvancedFilterMenu(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ((() -> Unit) -> Unit),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            content { expanded = false }
        }
    }
}

private fun AppFilter.zhLabel(): String = when (this) {
    AppFilter.All -> "全部应用"
    AppFilter.Online -> "联网"
    AppFilter.Offline -> "非联网"
    AppFilter.Whitelist -> "白名单"
    AppFilter.Blacklist -> "黑名单"
}

private fun InternetPermissionFilter.zhLabel(): String = when (this) {
    InternetPermissionFilter.All -> "全部"
    InternetPermissionFilter.Declared -> "声明联网"
    InternetPermissionFilter.NotDeclared -> "未声明联网"
}

private fun AppTypeFilter.zhLabel(): String = when (this) {
    AppTypeFilter.All -> "全部"
    AppTypeFilter.User -> "用户应用"
    AppTypeFilter.System -> "系统应用"
}

private fun LauncherFilter.zhLabel(): String = when (this) {
    LauncherFilter.All -> "全部"
    LauncherFilter.HasLauncher -> "有启动入口"
    LauncherFilter.NoLauncher -> "无启动入口"
}
