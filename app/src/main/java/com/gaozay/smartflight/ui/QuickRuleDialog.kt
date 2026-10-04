package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.gaozay.smartflight.R
import com.gaozay.smartflight.quickrule.*

@Composable
fun QuickRuleDialog(
    state: QuickRuleState,
    chooseScope: (QuickRuleScope) -> Unit,
    chooseMode: (QuickRuleMode) -> Unit,
    save: () -> Unit,
    dismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) dismiss() },
        properties = DialogProperties(dismissOnBackPress = !state.saving, dismissOnClickOutside = !state.saving),
        title = { Text(stringResource(R.string.quick_rule_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) CircularProgressIndicator()
                val target = state.target
                if (target != null) {
                    Text(target.app.label, style = MaterialTheme.typography.titleMedium)
                    ClassText(target.app.packageName)
                    target.activityName?.let { ClassText(it) }
                    Text(stringResource(if (target.suppliedTarget) R.string.quick_rule_supplied_target else R.string.quick_rule_detected_target), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.quick_rule_scope_label), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        QuickRuleScope.entries.forEach { option ->
                            QuickRuleRadio(stringResource(option.labelRes), state.scope == option,
                                !state.saving && (option == QuickRuleScope.App || target.activityName != null)) { chooseScope(option) }
                        }
                    }
                    if (target.activityName == null) Text(stringResource(R.string.quick_rule_activity_unavailable), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Column(Modifier.selectableGroup()) {
                        QuickRuleMode.entries.forEach { option ->
                            QuickRuleRadio(stringResource(option.labelRes), state.mode == option, !state.saving) { chooseMode(option) }
                        }
                    }
                    Text(stringResource(if (state.scope == QuickRuleScope.Activity) R.string.quick_rule_activity_help else R.string.quick_rule_app_help), style = MaterialTheme.typography.bodySmall)
                    if (state.automationPaused) Text(stringResource(R.string.quick_rule_automation_paused), style = MaterialTheme.typography.bodySmall)
                    if (state.scope == QuickRuleScope.Activity && !target.activityRulesEnabled) Text(stringResource(R.string.quick_rule_children_paused), style = MaterialTheme.typography.bodySmall)
                }
                state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = state.target != null && !state.loading && !state.saving) {
                Text(stringResource(if (state.saving) R.string.quick_rule_saving else R.string.activity_save))
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QuickRuleRadio(label: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled, Role.RadioButton, choose).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}
