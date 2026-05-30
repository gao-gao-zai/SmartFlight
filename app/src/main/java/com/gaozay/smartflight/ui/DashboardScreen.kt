package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.ExecutionLogItem
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.settings.AutomationDisableMode

private val automationDisableOptions = listOf(
    AutomationDisableMode.UntilAppSwitch,
    AutomationDisableMode.UntilScreenOff,
    AutomationDisableMode.For1Minute,
    AutomationDisableMode.For5Minutes,
    AutomationDisableMode.For10Minutes,
    AutomationDisableMode.For20Minutes,
    AutomationDisableMode.For30Minutes,
    AutomationDisableMode.Permanent,
)

@Composable
internal fun DashboardScreen(
    state: SmartFlightUiState,
    innerPadding: PaddingValues,
    onSetAutomationEnabled: (Boolean) -> Unit,
    onDisableAutomation: (AutomationDisableMode) -> Unit,
    onOpenApps: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MainStatusCard(state, onSetAutomationEnabled, onDisableAutomation) }
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
private fun MainStatusCard(
    state: SmartFlightUiState,
    onSetAutomationEnabled: (Boolean) -> Unit,
    onDisableAutomation: (AutomationDisableMode) -> Unit,
) {
    var disableMenuExpanded by rememberSaveable { mutableStateOf(false) }
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
                    Text(
                        state.automationDisableSummary ?: if (state.automationEnabled) "规则正在监听" else "所有自动动作已暂停",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.automationEnabled, onCheckedChange = onSetAutomationEnabled)
            }
            Box {
                OutlinedButton(
                    onClick = { disableMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.automationDisabled) "调整禁用模式" else "禁用")
                }
                DropdownMenu(
                    expanded = disableMenuExpanded,
                    onDismissRequest = { disableMenuExpanded = false },
                ) {
                    automationDisableOptions.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
                            onClick = {
                                disableMenuExpanded = false
                                onDisableAutomation(mode)
                            },
                        )
                    }
                }
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
