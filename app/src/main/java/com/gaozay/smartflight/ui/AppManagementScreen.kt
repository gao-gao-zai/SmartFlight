package com.gaozay.smartflight.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.apps.isOnline
import com.gaozay.smartflight.apps.sourceTag
import com.gaozay.smartflight.apps.statusLabel
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag

@Composable
fun AppManagementScreen(
    state: AppsUiState,
    innerPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onInternetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    onAppTypeFilterChange: (AppTypeFilter) -> Unit,
    onLauncherFilterChange: (LauncherFilter) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onRefreshApps: () -> Unit,
    onSetManualOnline: (String) -> Unit,
    onSetManualOffline: (String) -> Unit,
    onResetToDefault: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AppScopeSummary(state = state, onRefreshApps = onRefreshApps) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索应用名称或包名") },
            )
        }
        item {
            FilterSummaryRow(
                state = state,
                onFilterChange = onFilterChange,
                onInternetPermissionFilterChange = onInternetPermissionFilterChange,
                onAppTypeFilterChange = onAppTypeFilterChange,
                onLauncherFilterChange = onLauncherFilterChange,
                onClearAdvancedFilters = onClearAdvancedFilters,
            )
        }
        item {
            Text(
                text = "应用列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.apps.isEmpty()) {
            item { EmptyAppsCard(onRefreshApps = onRefreshApps) }
        } else {
            items(
            items = state.apps,
            key = { it.packageName },
        ) { app ->
                AppRow(
                    app = app,
                    onSetManualOnline = onSetManualOnline,
                    onSetManualOffline = onSetManualOffline,
                    onResetToDefault = onResetToDefault,
                )
            }
        }
    }
}

@Composable
private fun AppScopeSummary(
    state: AppsUiState,
    onRefreshApps: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "哪些应用会影响联网",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "联网列表决定应用是否会触发恢复联网，手动标记优先于自动识别",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onRefreshApps,
                    enabled = !state.isScanning,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (state.isScanning) "扫描中" else "扫描")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountPill(
                    label = "全部",
                    count = state.totalCount,
                    modifier = Modifier.weight(1f),
                )
                CountPill(
                    label = "联网",
                    count = state.onlineCount,
                    modifier = Modifier.weight(1f),
                )
                CountPill(
                    label = "非联网",
                    count = state.offlineCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CountPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FilterSummaryRow(
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
private fun AppRow(
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
private fun AppIcon(
    packageName: String,
    label: String,
    app: InstalledAppEntity,
) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(app.iconFallbackContainerColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text(
                text = label.take(1).ifBlank { "?" },
                fontWeight = FontWeight.Bold,
                color = app.iconFallbackContentColor(),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
private fun InstalledAppEntity.iconFallbackContainerColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.errorContainer
    isOnline() -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun InstalledAppEntity.iconFallbackContentColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.onErrorContainer
    isOnline() -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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

