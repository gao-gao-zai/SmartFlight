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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.ExecutionLogItem
import com.gaozay.smartflight.R
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
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
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
                EntryCard(Icons.Rounded.Apps, stringResource(R.string.app_scope), stringResource(R.string.choose_which_apps_are_treated_as_online), onOpenApps)
                EntryCard(Icons.AutoMirrored.Rounded.Rule, stringResource(R.string.automation_rules), stringResource(R.string.configure_screen_off_app_exit_and_wi_fi_exceptions), onOpenRules)
                EntryCard(Icons.Rounded.BugReport, stringResource(R.string.diagnostics_and_logs), stringResource(R.string.view_permissions_executors_and_recent_actions), onOpenDiagnostics)
                EntryCard(Icons.Rounded.Palette, stringResource(R.string.appearance), stringResource(R.string.adjust_theme_color_intensity_and_corner_style), onOpenAppearance)
                EntryCard(Icons.Rounded.Info, stringResource(R.string.about), stringResource(R.string.view_version_information_and_check_for_updates_manually), onOpenAbout)
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
                    Text(if (state.automationEnabled) stringResource(R.string.automation_running) else stringResource(R.string.automation_paused), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        state.automationDisableSummary ?: if (state.automationEnabled) stringResource(R.string.rules_are_monitoring) else stringResource(R.string.all_automatic_actions_are_paused),
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
                    Text(if (state.automationDisabled) stringResource(R.string.change_disable_mode) else stringResource(R.string.disable))
                }
                DropdownMenu(
                    expanded = disableMenuExpanded,
                    onDismissRequest = { disableMenuExpanded = false },
                ) {
                    automationDisableOptions.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes)) },
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
                StatusLine(stringResource(R.string.control_mode), state.currentMode)
                StatusLine(stringResource(R.string.execution_method), state.runtimeExecutor)
                StatusLine(stringResource(R.string.foreground_app), state.foregroundApp)
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
            Text(stringResource(R.string.current_explanation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text(stringResource(R.string.recent_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val latest = logs.firstOrNull()
            if (latest == null) Text(stringResource(R.string.no_automatic_actions_taken_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("${latest.action} · ${latest.result}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(latest.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
