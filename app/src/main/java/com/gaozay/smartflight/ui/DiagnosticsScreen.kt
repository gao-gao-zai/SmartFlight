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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessKind

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
        item { SettingsSection(stringResource(R.string.operating_requirements)) {
            AccessSummaryRow(
                title = stringResource(R.string.usage_access),
                summary = state.accessGateState.usageStatsAccess.summary,
                ready = state.accessGateState.usageStatsAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.usageStatsAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.usageStatsAccess }
                } else {
                    null
                },
            )
            AccessSummaryRow(
                title = stringResource(R.string.accessibility_monitoring),
                summary = state.accessGateState.accessibilityAccess.summary,
                ready = state.accessGateState.accessibilityAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.accessibilityAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.accessibilityAccess }
                } else {
                    null
                },
            )
            AccessSummaryRow(
                title = stringResource(R.string.notification_permission),
                summary = state.accessGateState.notificationAccess.summary,
                ready = state.accessGateState.notificationAccess.satisfiesRequirement,
                onBadgeClick = if (state.accessGateState.notificationAccess.actionType != AccessActionType.None) {
                    { selectedAccessResult = state.accessGateState.notificationAccess }
                } else {
                    null
                },
            )
            AccessSummaryRow(
                title = stringResource(R.string.battery_optimization),
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
                    onBadgeClick = if (it.actionType != AccessActionType.None || it.copyText != null || it.kind == AccessKind.Adb) {
                        { selectedAccessResult = it }
                    } else {
                        null
                    },
                )
            }
        } }
        item { SettingsSection(stringResource(R.string.executor_checks)) {
            Button(onClick = onRefreshAccessChecks, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.check_executors_again))
            }
            InfoRow(stringResource(R.string.current_active_executor), state.runtimeExecutor)
            InfoRow(stringResource(R.string.latest_result), state.runtimeLastResult)
            InfoRow(stringResource(R.string.latest_summary), state.runtimeLastCheck)
            InfoRow(stringResource(R.string.unified_network_state), state.unifiedNetworkState)
            InfoRow(stringResource(R.string.wi_fi_state), state.wifiStatus)
            InfoRow(stringResource(R.string.bluetooth_state), state.bluetoothStatus)
            InfoRow(stringResource(R.string.mobile_data_state), state.mobileDataStatus)
            if (!state.bluetoothReadable) {
                OutlinedButton(onClick = onRequestBluetoothPermission, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.request_bluetooth_state_permission))
                }
            }
        } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.advanced_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                    }
                    if (advancedExpanded) {
                        Text(stringResource(R.string.diagnostics_actions_description), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { pendingAction = "probe" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.probe_current_control_state)) }
                        OutlinedButton(onClick = { pendingAction = "toggle" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manually_toggle_current_mode)) }
                        OutlinedButton(onClick = onSimulateScreenOff, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.simulate_screen_off)) }
                        OutlinedButton(onClick = onSimulateScreenOn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.simulate_screen_on_cancel_delayed_disconnect)) }
                        OutlinedButton(onClick = { pendingAction = "clear" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clear_logs)) }
                    }
                }
            }
        }
        item { SettingsSection(stringResource(R.string.recent_logs)) {
            if (state.recentExecutionLogs.isEmpty()) Text(stringResource(R.string.no_manual_probes_or_toggle_actions_recorded_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
