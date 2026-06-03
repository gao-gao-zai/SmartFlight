package com.gaozay.smartflight.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult

@Composable
internal fun DiagnosticsScreen(
    state: SmartFlightUiState,
    innerPadding: PaddingValues,
    onRefreshAccessChecks: () -> Unit,
    onProbeCurrentNetworkControlState: () -> Unit,
    onToggleCurrentNetworkControlState: () -> Unit,
    onSimulateScreenOff: () -> Unit,
    onSimulateScreenOn: () -> Unit,
    onClearExecutionLogs: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
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
                title = "无障碍监听",
                summary = state.accessGateState.accessibilityAccess.summary,
                ready = state.accessGateState.accessibilityAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.accessibilityAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.accessibilityAccess }
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
            InfoRow("当前统一网络状态", state.unifiedNetworkState)
            InfoRow("Wi‑Fi 状态", state.wifiStatus)
            InfoRow("蓝牙状态", state.bluetoothStatus)
            InfoRow("移动数据状态", state.mobileDataStatus)
            if (!state.bluetoothReadable) {
                OutlinedButton(onClick = onRequestBluetoothPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("请求蓝牙状态权限")
                }
            }
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
                        OutlinedButton(onClick = { pendingAction = "probe" }, modifier = Modifier.fillMaxWidth()) { Text("探测当前控制状态") }
                        OutlinedButton(onClick = { pendingAction = "toggle" }, modifier = Modifier.fillMaxWidth()) { Text("手动切换当前模式") }
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
        ConfirmActionDialog(action = action, modeLabel = state.currentMode, onDismiss = { pendingAction = null }) {
            when (action) {
                "probe" -> onProbeCurrentNetworkControlState()
                "toggle" -> onToggleCurrentNetworkControlState()
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
            onOpenAccessibilitySettings = {
                onOpenAccessibilitySettings()
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
