package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled

@Composable
internal fun RulesScreen(
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
            SwitchRow("自动化总开关", "关闭后永久禁用自动动作", settings.automationEnabled) { enabled ->
                onUpdateSettings { s ->
                    if (enabled) s.withAutomationEnabled() else s.withAutomationDisabled(AutomationDisableMode.Permanent)
                }
            }
            SwitchRow(
                "外部联网变化时暂停",
                "检测到不是 SmartFlight 触发的联网状态变化后，暂停自动化直到应用切换",
                settings.pauseAutomationOnExternalNetworkChange,
            ) {
                onUpdateSettings { s -> s.copy(pauseAutomationOnExternalNetworkChange = it) }
            }
            ChoiceRow("联网控制方式", NetworkControlMode.entries, settings.networkControlMode, onSetNetworkControlMode)
            ChoiceRow("执行器偏好", ExecutorType.entries.filterNot { it == ExecutorType.Unavailable }, settings.preferredExecutorType, onSetPreferredExecutorType)
        } }
        item { SettingsSection("应用触发") {
            SwitchRow("启动目标应用时恢复联网", "联网应用进入前台时按当前模式恢复联网", settings.reconnectOnTargetAppLaunch) { onUpdateSettings { s -> s.copy(reconnectOnTargetAppLaunch = it) } }
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
