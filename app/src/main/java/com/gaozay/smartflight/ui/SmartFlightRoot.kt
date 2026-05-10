package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.ExecutionLogItem
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.settings.UserSettings
import java.text.DateFormat
import java.util.Date

private enum class SmartFlightScreen(val title: String) {
    Dashboard("控制台"), Apps("应用范围"), Rules("自动化规则"), Diagnostics("诊断与日志"), Appearance("外观设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlightRoot(
    state: SmartFlightUiState,
    appsState: AppsUiState,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onSetNetworkControlMode: (NetworkControlMode) -> Unit,
    onSetPreferredExecutorType: (ExecutorType) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetThemePalette: (ThemePalette) -> Unit,
    onSetCustomSeedColor: (Int) -> Unit,
    onSetThemeIntensity: (ThemeIntensity) -> Unit,
    onSetCornerStyle: (CornerStyle) -> Unit,
    onSetAutomationEnabled: (Boolean) -> Unit,
    onSetMonitorForegroundWhenScreenOff: (Boolean) -> Unit,
    onAppQueryChange: (String) -> Unit,
    onAppFilterChange: (AppFilter) -> Unit,
    onAppInternetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    onAppTypeFilterChange: (AppTypeFilter) -> Unit,
    onAppLauncherFilterChange: (LauncherFilter) -> Unit,
    onClearAppAdvancedFilters: () -> Unit,
    onRefreshApps: () -> Unit,
    onSetAppManualOnline: (String) -> Unit,
    onSetAppManualOffline: (String) -> Unit,
    onResetAppToDefault: (String) -> Unit,
    onRefreshAccessChecks: () -> Unit,
    onProbeAirplaneModeState: () -> Unit,
    onToggleAirplaneModeState: () -> Unit,
    onSimulateScreenOff: () -> Unit,
    onSimulateScreenOn: () -> Unit,
    onClearExecutionLogs: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onAutoGrantCompanionPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(SmartFlightScreen.Dashboard) }
    var forceShowAccessGate by rememberSaveable { mutableStateOf(false) }
    val showAccessGate = forceShowAccessGate || !state.accessGateState.canEnterApp
    if (showAccessGate) {
        Scaffold(topBar = { SmartFlightTopBar("SmartFlight 接入检查") }) { innerPadding ->
            Surface(Modifier.fillMaxSize().padding(innerPadding)) {
                AccessGateScreen(
                    state = state.accessGateState,
                    onRefresh = onRefreshAccessChecks,
                    onContinueToApp = { forceShowAccessGate = false },
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onProbeRootAccess = onProbeRootAccess,
                    onSetAdbBootstrapped = onSetAdbBootstrapped,
                    onAutoGrantCompanionPermissions = {
                        forceShowAccessGate = true
                        onAutoGrantCompanionPermissions()
                    },
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                )
            }
        }
        return
    }
    Scaffold(
        topBar = {
            if (screen == SmartFlightScreen.Dashboard) DashboardTopBar { screen = SmartFlightScreen.Appearance }
            else SmartFlightTopBar(screen.title) { screen = SmartFlightScreen.Dashboard }
        },
    ) { innerPadding ->
        Surface(Modifier.fillMaxSize()) {
            when (screen) {
                SmartFlightScreen.Dashboard -> DashboardScreen(state, innerPadding, onSetAutomationEnabled, { screen = SmartFlightScreen.Apps }, { screen = SmartFlightScreen.Rules }, { screen = SmartFlightScreen.Diagnostics })
                SmartFlightScreen.Apps -> AppManagementScreen(
                    state = appsState,
                    innerPadding = innerPadding,
                    onQueryChange = onAppQueryChange,
                    onFilterChange = onAppFilterChange,
                    onInternetPermissionFilterChange = onAppInternetPermissionFilterChange,
                    onAppTypeFilterChange = onAppTypeFilterChange,
                    onLauncherFilterChange = onAppLauncherFilterChange,
                    onClearAdvancedFilters = onClearAppAdvancedFilters,
                    onRefreshApps = onRefreshApps,
                    onSetManualOnline = onSetAppManualOnline,
                    onSetManualOffline = onSetAppManualOffline,
                    onResetToDefault = onResetAppToDefault,
                )
                SmartFlightScreen.Rules -> RulesScreen(state.settings, innerPadding, onUpdateSettings, onSetNetworkControlMode, onSetPreferredExecutorType, onSetMonitorForegroundWhenScreenOff)
                SmartFlightScreen.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    innerPadding = innerPadding,
                    onRefreshAccessChecks = onRefreshAccessChecks,
                    onProbeAirplaneModeState = onProbeAirplaneModeState,
                    onToggleAirplaneModeState = onToggleAirplaneModeState,
                    onSimulateScreenOff = onSimulateScreenOff,
                    onSimulateScreenOn = onSimulateScreenOn,
                    onClearExecutionLogs = onClearExecutionLogs,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onProbeRootAccess = onProbeRootAccess,
                    onSetAdbBootstrapped = onSetAdbBootstrapped,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                )
                SmartFlightScreen.Appearance -> AppearanceScreen(state.settings, innerPadding, onSetThemeMode, onSetThemePalette, onSetCustomSeedColor, onSetThemeIntensity, onSetCornerStyle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(onOpenAppearance: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("自动飞行", fontWeight = FontWeight.Bold)
                Text("SmartFlight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("外观设置") },
                        leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                        onClick = { expanded = false; onOpenAppearance() },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartFlightTopBar(title: String, onBack: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        },
    )
}

@Composable
private fun DashboardScreen(
    state: SmartFlightUiState,
    innerPadding: PaddingValues,
    onSetAutomationEnabled: (Boolean) -> Unit,
    onOpenApps: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MainStatusCard(state, onSetAutomationEnabled) }
        item { ExplanationCard(state.triggerSummary) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryCard(Icons.Rounded.Apps, "应用范围", "设置哪些应用会被视为联网", onOpenApps)
                EntryCard(Icons.AutoMirrored.Rounded.Rule, "自动化规则", "配置息屏、离开应用和 Wi‑Fi 例外", onOpenRules)
                EntryCard(Icons.Rounded.BugReport, "诊断与日志", "查看权限、执行器和最近动作", onOpenDiagnostics)
            }
        }
        item { RecentActionCard(state.recentExecutionLogs, onOpenDiagnostics) }
    }
}

@Composable
private fun MainStatusCard(state: SmartFlightUiState, onSetAutomationEnabled: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.automationEnabled) "自动化运行中" else "自动化已暂停", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (state.automationEnabled) "规则正在监听" else "所有自动动作已暂停", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.automationEnabled, onCheckedChange = onSetAutomationEnabled)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusLine("控制模式", state.currentMode)
                StatusLine("执行方式", state.runtimeExecutor)
                StatusLine("前台应用", state.foregroundApp)
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExplanationCard(summary: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前解释", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun RecentActionCard(logs: List<ExecutionLogItem>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("最近动作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val latest = logs.firstOrNull()
            if (latest == null) Text("尚未执行任何自动动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("${latest.action} · ${latest.result}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(latest.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RulesScreen(
    settings: UserSettings,
    innerPadding: PaddingValues,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onSetNetworkControlMode: (NetworkControlMode) -> Unit,
    onSetPreferredExecutorType: (ExecutorType) -> Unit,
    onSetMonitorForegroundWhenScreenOff: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RulePreviewCard(settings) }
        item { SettingsSection("总行为") {
            SwitchRow("自动化总开关", "关闭后所有自动动作暂停", settings.automationEnabled) { onUpdateSettings { s -> s.copy(automationEnabled = it) } }
            ChoiceRow("联网控制方式", NetworkControlMode.entries, settings.networkControlMode, onSetNetworkControlMode)
            ChoiceRow("执行器偏好", ExecutorType.entries.filterNot { it == ExecutorType.Unavailable }, settings.preferredExecutorType, onSetPreferredExecutorType)
        } }
        item { SettingsSection("应用触发") {
            SwitchRow("启动目标应用时恢复联网", "联网应用进入前台时关闭飞行模式", settings.reconnectOnTargetAppLaunch) { onUpdateSettings { s -> s.copy(reconnectOnTargetAppLaunch = it) } }
            SwitchRow("离开目标应用后断网", "离开联网应用后等待一段时间再断网", settings.appExitDisconnectEnabled) { onUpdateSettings { s -> s.copy(appExitDisconnectEnabled = it) } }
            NumberRow("离开后延迟秒数", settings.appExitDelaySeconds) { onUpdateSettings { s -> s.copy(appExitDelaySeconds = it.coerceIn(0, 600)) } }
        } }
        item { SettingsSection("息屏触发") {
            SwitchRow("息屏后自动断网", "屏幕关闭后按延迟执行断网", settings.screenOffDisconnectEnabled) { onUpdateSettings { s -> s.copy(screenOffDisconnectEnabled = it) } }
            NumberRow("息屏后延迟秒数", settings.screenOffDelaySeconds) { onUpdateSettings { s -> s.copy(screenOffDelaySeconds = it.coerceIn(0, 3600)) } }
            SwitchRow("息屏时继续监听前台应用", "更及时但更耗电", settings.monitorForegroundWhenScreenOff, onSetMonitorForegroundWhenScreenOff)
            SwitchRow("亮屏后不自动恢复联网", "亮屏本身不触发联网恢复", settings.disableScreenOnReconnect) { onUpdateSettings { s -> s.copy(disableScreenOnReconnect = it) } }
            SwitchRow("解锁后不自动恢复联网", "只由目标应用触发恢复联网", settings.disableUnlockReconnect) { onUpdateSettings { s -> s.copy(disableUnlockReconnect = it) } }
        } }
        item { SettingsSection("Wi‑Fi 例外与状态保留") {
            SwitchRow("连接 Wi‑Fi 时不自动恢复联网", "避免在 Wi‑Fi 环境下额外切换飞行模式", settings.skipReconnectOnWifi) { onUpdateSettings { s -> s.copy(skipReconnectOnWifi = it) } }
            SwitchRow("连接 Wi‑Fi 时不自动断网", "Wi‑Fi 可用时跳过自动断网动作", settings.skipDisconnectOnWifi) { onUpdateSettings { s -> s.copy(skipDisconnectOnWifi = it) } }
            SwitchRow("切换时保留 Wi‑Fi 状态", "不同系统上可能失败", settings.preserveWifiState) { onUpdateSettings { s -> s.copy(preserveWifiState = it) } }
            SwitchRow("切换时保留蓝牙状态", "不同系统上可能失败", settings.preserveBluetoothState) { onUpdateSettings { s -> s.copy(preserveBluetoothState = it) } }
        } }
        item { SettingsSection("动作提示") {
            SwitchRow("恢复联网时提示", "自动恢复联网后显示一条短提示", settings.showReconnectPrompt) { onUpdateSettings { s -> s.copy(showReconnectPrompt = it) } }
            TextInputRow("恢复联网提示内容", settings.reconnectPromptText) { value -> onUpdateSettings { s -> s.copy(reconnectPromptText = value) } }
            SwitchRow("断网时提示", "自动断网后显示一条短提示", settings.showDisconnectPrompt) { onUpdateSettings { s -> s.copy(showDisconnectPrompt = it) } }
            TextInputRow("断网提示内容", settings.disconnectPromptText) { value -> onUpdateSettings { s -> s.copy(disconnectPromptText = value) } }
        } }
    }
}

@Composable
private fun RulePreviewCard(settings: UserSettings) {
    val preview = buildString {
        append(if (settings.reconnectOnTargetAppLaunch) "启动目标应用时恢复联网" else "启动目标应用时不自动恢复联网")
        append("；")
        append(if (settings.appExitDisconnectEnabled) "离开目标应用 ${settings.appExitDelaySeconds} 秒后断网" else "离开目标应用后不断网")
        append("；")
        append(if (settings.screenOffDisconnectEnabled) "息屏 ${settings.screenOffDelaySeconds} 秒后断网" else "息屏后不断网")
        if (settings.skipDisconnectOnWifi) append("；连接 Wi‑Fi 时跳过断网")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("规则预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(preview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("已自动保存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    state: SmartFlightUiState,
    innerPadding: PaddingValues,
    onRefreshAccessChecks: () -> Unit,
    onProbeAirplaneModeState: () -> Unit,
    onToggleAirplaneModeState: () -> Unit,
    onSimulateScreenOff: () -> Unit,
    onSimulateScreenOn: () -> Unit,
    onClearExecutionLogs: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAccessResult by remember { mutableStateOf<AccessCheckResult?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsSection("运行条件") {
            AccessSummaryRow(
                title = "使用情况访问",
                summary = state.accessGateState.usageStatsAccess.summary,
                ready = state.accessGateState.usageStatsAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.usageStatsAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.usageStatsAccess }
                } else {
                    null
                },
            )
            AccessSummaryRow(
                title = "通知权限",
                summary = state.accessGateState.notificationAccess.summary,
                ready = state.accessGateState.notificationAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.notificationAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.notificationAccess }
                } else {
                    null
                },
            )
            AccessSummaryRow(
                title = "电池优化",
                summary = state.accessGateState.batteryOptimization.summary,
                ready = state.accessGateState.batteryOptimization.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.batteryOptimization.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.batteryOptimization }
                } else {
                    null
                },
            )
            state.accessGateState.advancedAccess.checks.forEach {
                AccessSummaryRow(
                    title = it.title,
                    summary = it.summary,
                    ready = it.satisfiesRequirement,
                    onBadgeClick = if (it.actionType != AccessActionType.None || it.copyText != null || it.title == "ADB 初始化") {
                        { selectedAccessResult = it }
                    } else {
                        null
                    },
                )
            }
        } }
        item { SettingsSection("执行器检测") {
            Button(onClick = onRefreshAccessChecks, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("重新检测执行器")
            }
            InfoRow("当前实际执行器", state.runtimeExecutor)
            InfoRow("最近结果", state.runtimeLastResult)
            InfoRow("最近摘要", state.runtimeLastCheck)
        } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }, verticalAlignment = Alignment.CenterVertically) {
                        Text("高级操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                    }
                    if (advancedExpanded) {
                        Text("这里的操作会直接改变设备联网状态，仅用于排障。", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { pendingAction = "probe" }, modifier = Modifier.fillMaxWidth()) { Text("探测飞行模式状态") }
                        OutlinedButton(onClick = { pendingAction = "toggle" }, modifier = Modifier.fillMaxWidth()) { Text("手动切换飞行模式") }
                        OutlinedButton(onClick = onSimulateScreenOff, modifier = Modifier.fillMaxWidth()) { Text("模拟息屏") }
                        OutlinedButton(onClick = onSimulateScreenOn, modifier = Modifier.fillMaxWidth()) { Text("模拟亮屏 / 取消延迟断网") }
                        OutlinedButton(onClick = { pendingAction = "clear" }, modifier = Modifier.fillMaxWidth()) { Text("清空日志") }
                    }
                }
            }
        }
        item { SettingsSection("最近日志") {
            if (state.recentExecutionLogs.isEmpty()) Text("尚未记录任何手动探测或切换动作。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else state.recentExecutionLogs.forEach { ExecutionLogCard(it) }
        } }
    }
    pendingAction?.let { action ->
        ConfirmActionDialog(action = action, onDismiss = { pendingAction = null }) {
            when (action) {
                "probe" -> onProbeAirplaneModeState()
                "toggle" -> onToggleAirplaneModeState()
                "clear" -> onClearExecutionLogs()
            }
            pendingAction = null
        }
    }
    selectedAccessResult?.let { result ->
        AccessHandlingDialog(
            result = result,
            onDismiss = { selectedAccessResult = null },
            onRefresh = {
                onRefreshAccessChecks()
                selectedAccessResult = null
            },
            onRequestShizukuPermission = {
                onRequestShizukuPermission()
                selectedAccessResult = null
            },
            onProbeRootAccess = {
                onProbeRootAccess()
                selectedAccessResult = null
            },
            onCopyAdbCommands = {
                result.copyText?.let { clipboardManager.setText(AnnotatedString(it)) }
            },
            onSetAdbBootstrapped = {
                onSetAdbBootstrapped(it)
                selectedAccessResult = null
            },
            onOpenUsageAccessSettings = {
                onOpenUsageAccessSettings()
                selectedAccessResult = null
            },
            onOpenNotificationSettings = {
                onOpenNotificationSettings()
                selectedAccessResult = null
            },
            onOpenBatteryOptimizationSettings = {
                onOpenBatteryOptimizationSettings()
                selectedAccessResult = null
            },
        )
    }
}

@Composable
private fun AppearanceScreen(
    settings: UserSettings,
    innerPadding: PaddingValues,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetThemePalette: (ThemePalette) -> Unit,
    onSetCustomSeedColor: (Int) -> Unit,
    onSetThemeIntensity: (ThemeIntensity) -> Unit,
    onSetCornerStyle: (CornerStyle) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ThemePreviewCard(settings) }
        item { SettingsSection("显示模式") { ChoiceRow("模式", ThemeMode.entries, settings.themeMode, onSetThemeMode) } }
        item { SettingsSection("配色风格") {
            enumValues<ThemePalette>().forEach { palette: ThemePalette ->
                OptionRow(palette.label, settings.themePalette == palette, Color(palette.seedColorArgb)) { onSetThemePalette(palette) }
            }
        } }
        item { SettingsSection("自定义 seed color") {
            listOf<Int>(0xFF545D6D.toInt(), 0xFF657181.toInt(), 0xFF2F3948.toInt(), 0xFFA1859B.toInt(), 0xFF5E6D5A.toInt(), 0xFF73545D.toInt()).forEach { seed: Int ->
                OptionRow(
                    title = "Seed #${Integer.toHexString(seed).takeLast(6).uppercase()}",
                    selected = settings.themePalette == ThemePalette.Custom && settings.customSeedColorArgb == seed,
                    color = Color(seed),
                    onClick = { onSetCustomSeedColor(seed) },
                )
            }
        } }
        item { SettingsSection("显示强度") {
            ChoiceRow("颜色强度", ThemeIntensity.entries, settings.themeIntensity, onSetThemeIntensity)
            ChoiceRow("圆角风格", CornerStyle.entries, settings.cornerStyle, onSetCornerStyle)
        } }
    }
}

@Composable
private fun ThemePreviewCard(settings: UserSettings) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${settings.themePalette.label} · ${settings.themeMode.label}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge("成功", StatusKind.Success)
                StatusBadge("注意", StatusKind.Warning)
                StatusBadge("失败", StatusKind.Error)
            }
            Button(onClick = {}) { Text("主按钮") }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberRow(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { onValueChange(value - 5) }) { Text("−") }
        Text("$value 秒", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onValueChange(value + 5) }) { Text("+") }
    }
}

@Composable
private fun TextInputRow(title: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        singleLine = true,
    )
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, onSelect: (T) -> Unit) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        options.forEach { option ->
            val label = when (option) {
                is NetworkControlMode -> option.label
                is ExecutorType -> option.label
                is ThemeMode -> option.label
                is ThemeIntensity -> option.label
                is CornerStyle -> option.label
                else -> option.name
            }
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(label) })
        }
    }
}

@Composable
private fun OptionRow(title: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(12.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun AccessSummaryRow(
    title: String,
    summary: String,
    ready: Boolean,
    onBadgeClick: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(
            text = if (ready) "正常" else "处理",
            kind = if (ready) StatusKind.Success else StatusKind.Warning,
            onClick = onBadgeClick,
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class StatusKind { Success, Warning, Error }

@Composable
private fun StatusBadge(text: String, kind: StatusKind, onClick: (() -> Unit)? = null) {
    val color = when (kind) {
        StatusKind.Success -> Color(0xFF2E7D5B)
        StatusKind.Warning -> Color(0xFF9A6A00)
        StatusKind.Error -> MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = { onClick?.invoke() },
        label = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = when (kind) {
                    StatusKind.Success -> Icons.Rounded.CheckCircle
                    StatusKind.Warning -> Icons.Rounded.Schedule
                    StatusKind.Error -> Icons.Rounded.Error
                },
                contentDescription = null,
                tint = color,
            )
        },
        enabled = onClick != null,
    )
}

@Composable
private fun AccessHandlingDialog(
    result: AccessCheckResult,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onCopyAdbCommands: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (result.satisfiesRequirement) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                contentDescription = null,
            )
        },
        title = { Text(result.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(result.summary)
                Text(result.recommendation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (result.title) {
                    "使用情况访问权限" -> TextButton(onClick = onOpenUsageAccessSettings) { Text("去设置") }
                    "通知权限" -> TextButton(onClick = onOpenNotificationSettings) { Text("去设置") }
                    "电池优化" -> TextButton(onClick = onOpenBatteryOptimizationSettings) { Text("去设置") }
                    "Shizuku" -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onRequestShizukuPermission) { Text("请求授权") }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text("重新检测") }
                        else -> Unit
                    }
                    "Root" -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onProbeRootAccess) { Text("测试授权") }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text("重新检测") }
                        else -> Unit
                    }
                    "ADB 初始化" -> {
                        result.copyText?.let {
                            TextButton(onClick = onCopyAdbCommands) {
                                Text(result.copyLabel ?: "复制命令")
                            }
                        }
                        TextButton(onClick = { onSetAdbBootstrapped(!result.satisfiesRequirement) }) {
                            Text(if (result.satisfiesRequirement) "重置状态" else "标记完成")
                        }
                    }
                    else -> if (result.actionType == AccessActionType.Refresh) {
                        TextButton(onClick = onRefresh) { Text("重新检测") }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ExecutionLogCard(item: ExecutionLogItem) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(item.result, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(DateFormat.getDateTimeInstance().format(Date(item.timestampMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmActionDialog(action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title = when (action) {
        "probe" -> "探测飞行模式状态"
        "toggle" -> "手动切换飞行模式"
        "clear" -> "清空全部日志"
        else -> "确认操作"
    }
    val description = when (action) {
        "probe" -> "即将读取设备飞行模式状态，并写入一条诊断日志。"
        "toggle" -> "即将直接改变设备联网状态。该操作仅用于排障，并会写入日志。"
        "clear" -> "即将清空当前保存的执行日志，此操作不可从应用内恢复。"
        else -> "请确认是否继续。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (action == "clear") Icons.Rounded.Error else Icons.Rounded.PowerSettingsNew, contentDescription = null) },
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
