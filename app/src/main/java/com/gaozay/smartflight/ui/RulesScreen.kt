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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.ForegroundMonitorMode
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
    onSetForegroundMonitorMode: (ForegroundMonitorMode) -> Unit,
    onSetMonitorForegroundWhenScreenOff: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RulePreviewCard(settings) }
        item { SettingsSection(stringResource(R.string.general_behavior)) {
            SwitchRow(stringResource(R.string.enable_automation), stringResource(R.string.turning_this_off_permanently_disables_automatic_actions), settings.automationEnabled) { enabled ->
                onUpdateSettings { s ->
                    if (enabled) s.withAutomationEnabled() else s.withAutomationDisabled(AutomationDisableMode.Permanent)
                }
            }
            SwitchRow(
                stringResource(R.string.pause_on_external_connectivity_changes),
                stringResource(R.string.rule_pause_external_change_description),
                settings.pauseAutomationOnExternalNetworkChange,
            ) {
                onUpdateSettings { s -> s.copy(pauseAutomationOnExternalNetworkChange = it) }
            }
            ChoiceRow(stringResource(R.string.connectivity_control_method), NetworkControlMode.entries, settings.networkControlMode, onSetNetworkControlMode)
            ChoiceRow(stringResource(R.string.preferred_executor), ExecutorType.entries.filterNot { it == ExecutorType.Unavailable }, settings.preferredExecutorType, onSetPreferredExecutorType)
        } }
        item { SettingsSection(stringResource(R.string.app_triggers)) {
            ChoiceRow(stringResource(R.string.foreground_monitoring_method), ForegroundMonitorMode.entries, settings.foregroundMonitorMode, onSetForegroundMonitorMode)
            SwitchRow(stringResource(R.string.reconnect_when_launching_a_target_app), stringResource(R.string.rule_target_reconnect_description), settings.reconnectOnTargetAppLaunch) { onUpdateSettings { s -> s.copy(reconnectOnTargetAppLaunch = it) } }
            SwitchRow(stringResource(R.string.disconnect_after_leaving_a_target_app), stringResource(R.string.wait_before_disconnecting_after_leaving_an_online_app), settings.appExitDisconnectEnabled) { onUpdateSettings { s -> s.copy(appExitDisconnectEnabled = it) } }
            NumberRow(stringResource(R.string.app_exit_delay_in_seconds), settings.appExitDelaySeconds) { onUpdateSettings { s -> s.copy(appExitDelaySeconds = it.coerceIn(0, 600)) } }
        } }
        item { SettingsSection(stringResource(R.string.screen_off_triggers)) {
            SwitchRow(stringResource(R.string.disconnect_automatically_when_the_screen_turns_off), stringResource(R.string.disconnect_after_the_configured_delay_when_the_screen_turns_off), settings.screenOffDisconnectEnabled) { onUpdateSettings { s -> s.copy(screenOffDisconnectEnabled = it) } }
            NumberRow(stringResource(R.string.screen_off_delay_in_seconds), settings.screenOffDelaySeconds) { onUpdateSettings { s -> s.copy(screenOffDelaySeconds = it.coerceIn(0, 3600)) } }
            SwitchRow(stringResource(R.string.keep_monitoring_foreground_apps_with_the_screen_off), stringResource(R.string.more_responsive_but_uses_more_battery), settings.monitorForegroundWhenScreenOff, onSetMonitorForegroundWhenScreenOff)
            SwitchRow(stringResource(R.string.do_not_reconnect_automatically_when_the_screen_turns_on), stringResource(R.string.turning_on_the_screen_alone_does_not_trigger_reconnecting), settings.disableScreenOnReconnect) { onUpdateSettings { s -> s.copy(disableScreenOnReconnect = it) } }
            SwitchRow(stringResource(R.string.do_not_reconnect_automatically_when_unlocking), stringResource(R.string.only_target_apps_trigger_reconnecting), settings.disableUnlockReconnect) { onUpdateSettings { s -> s.copy(disableUnlockReconnect = it) } }
        } }
        item { SettingsSection(stringResource(R.string.wi_fi_exceptions_and_state_preservation)) {
            SwitchRow(stringResource(R.string.do_not_reconnect_automatically_while_connected_to_wi_fi), stringResource(R.string.avoid_extra_airplane_mode_switches_while_using_wi_fi), settings.skipReconnectOnWifi) { onUpdateSettings { s -> s.copy(skipReconnectOnWifi = it) } }
            SwitchRow(stringResource(R.string.do_not_disconnect_automatically_while_connected_to_wi_fi), stringResource(R.string.skip_automatic_disconnects_when_wi_fi_is_available), settings.skipDisconnectOnWifi) { onUpdateSettings { s -> s.copy(skipDisconnectOnWifi = it) } }
            SwitchRow(stringResource(R.string.preserve_wi_fi_state_when_switching), stringResource(R.string.may_fail_on_some_systems), settings.preserveWifiState) { onUpdateSettings { s -> s.copy(preserveWifiState = it) } }
            SwitchRow(stringResource(R.string.preserve_bluetooth_state_when_switching), stringResource(R.string.may_fail_on_some_systems), settings.preserveBluetoothState) { onUpdateSettings { s -> s.copy(preserveBluetoothState = it) } }
        } }
        item { SettingsSection(stringResource(R.string.action_prompts)) {
            SwitchRow(stringResource(R.string.show_a_prompt_when_reconnecting), stringResource(R.string.show_a_brief_prompt_after_automatic_reconnection), settings.showReconnectPrompt) { onUpdateSettings { s -> s.copy(showReconnectPrompt = it) } }
            TextInputRow(stringResource(R.string.reconnect_prompt_text), settings.reconnectPromptText, placeholder = stringResource(R.string.prompt_reconnected_default)) { value -> onUpdateSettings { s -> s.copy(reconnectPromptText = value) } }
            SwitchRow(stringResource(R.string.show_a_prompt_when_disconnecting), stringResource(R.string.show_a_brief_prompt_after_automatic_disconnection), settings.showDisconnectPrompt) { onUpdateSettings { s -> s.copy(showDisconnectPrompt = it) } }
            TextInputRow(stringResource(R.string.disconnect_prompt_text), settings.disconnectPromptText, placeholder = stringResource(R.string.prompt_disconnected_default)) { value -> onUpdateSettings { s -> s.copy(disconnectPromptText = value) } }
        } }
    }
}

@Composable
private fun RulePreviewCard(settings: UserSettings) {
    val preview = buildString {
        append(if (settings.reconnectOnTargetAppLaunch) stringResource(R.string.reconnect_when_launching_a_target_app) else stringResource(R.string.do_not_reconnect_automatically_when_launching_a_target_app))
        append(stringResource(R.string.summary_separator))
        append(if (settings.appExitDisconnectEnabled) pluralStringResource(R.plurals.disconnect_seconds_after_leaving_a_target_app, (settings.appExitDelaySeconds).toInt(), settings.appExitDelaySeconds) else stringResource(R.string.do_not_disconnect_after_leaving_a_target_app))
        append(stringResource(R.string.summary_separator))
        append(if (settings.screenOffDisconnectEnabled) pluralStringResource(R.plurals.disconnect_seconds_after_screen_off, (settings.screenOffDelaySeconds).toInt(), settings.screenOffDelaySeconds) else stringResource(R.string.do_not_disconnect_after_screen_off))
        if (settings.skipDisconnectOnWifi) append(stringResource(R.string.skip_disconnects_while_connected_to_wi_fi))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.rule_preview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(preview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(R.string.saved_automatically), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f))
        }
    }
}
