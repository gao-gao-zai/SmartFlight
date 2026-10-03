package com.gaozay.smartflight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.gaozay.smartflight.apps.sourceTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.*
import com.gaozay.smartflight.data.local.entity.*
import com.gaozay.smartflight.runtime.*
import java.text.DateFormat
import java.util.Date

private enum class ActivityPage(val titleRes: Int) {
    Detail(R.string.activity_details_title), Record(R.string.activity_record_title), Diagnostics(R.string.activity_diagnostics_title)
}
private enum class ActivityListFilter(val labelRes: Int) {
    All(R.string.activity_all), Configured(R.string.activity_configured), Visited(R.string.activity_visited)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityManagementScreen(state: ActivityManagementState, actions: ActivityActions, system: SystemIntentActions, appsActions: AppsActions) {
    val pkg = state.packageName ?: return
    var page by rememberSaveable(pkg) { mutableStateOf(ActivityPage.Detail) }
    var selectedClass by rememberSaveable(pkg) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(pkg) { mutableStateOf("") }
    var filter by rememberSaveable(pkg) { mutableStateOf(ActivityListFilter.All) }
    var showDisabled by rememberSaveable(pkg) { mutableStateOf(false) }
    selectedClass?.let { name ->
        ActivityEditorScreen(state, name, actions) { selectedClass = null }
        return
    }
    val back: () -> Unit = {
        if (page == ActivityPage.Detail) actions.selectApp(null)
        else { if (page == ActivityPage.Record) actions.stopRecording(); page = ActivityPage.Detail }
    }
    BackHandler(onBack = back)
    Scaffold(topBar = { ActivityTopBar(stringResource(page.titleRes), back) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        state.app?.let { AppIcon(pkg, it.label, it) }
                        Text(state.app?.label ?: pkg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    ClassText(pkg)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            when (page) {
                ActivityPage.Detail -> {
                    item { SettingsSection(stringResource(R.string.activity_app_default_title)) {
                        val app = state.app
                        if (app != null) {
                            val default = if (app.isInWhitelist) ActivityRuleMode.Online else if (app.isInBlacklist) ActivityRuleMode.Offline else ActivityRuleMode.FollowApp
                            ActivityRuleMode.entries.forEach { mode ->
                                RuleRadio(if (mode == ActivityRuleMode.FollowApp) stringResource(R.string.activity_auto_default) else stringResource(mode.labelRes),
                                    default == mode) {
                                    when (mode) {
                                        ActivityRuleMode.Online -> appsActions.setManualOnline(pkg)
                                        ActivityRuleMode.Offline -> appsActions.setManualOffline(pkg)
                                        ActivityRuleMode.FollowApp -> appsActions.resetToDefault(pkg)
                                    }
                                }
                            }
                        } else if (!state.isScanning && state.details.config != null) Text(stringResource(R.string.activity_uninstalled))
                    } }
                    item { SettingsSection(stringResource(R.string.activity_editor_title)) {
                        RuleSwitch(stringResource(R.string.activity_enable_rules), state.details.config?.rulesEnabled != false, actions.setEnabled)
                        if (state.details.config?.rulesEnabled == false) Text(stringResource(R.string.activity_paused_description))
                        val overrides = state.details.rules.filter { it.mode != ActivityRuleMode.FollowApp.name }
                        val valid = overrides.count { rule -> state.app != null && state.details.config?.scanError == null &&
                            state.details.components.any { it.className == rule.activityName && it.isPresent && it.isEnabled && it.isDeclared } }
                        Text(stringResource(R.string.activity_counts, state.details.components.count { it.isPresent && it.isDeclared }, overrides.size, valid))
                        if (valid < overrides.size) Text(stringResource(R.string.activity_invalid_count, overrides.size - valid), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.activity_scan_scope), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.activity_scan_time, activityTime(state.details.config?.lastScannedAtMillis ?: 0)))
                        if (state.details.config?.scanError != null) Text(stringResource(R.string.activity_scan_kept), color = MaterialTheme.colorScheme.error)
                        if (state.details.config?.needsReview == true) {
                            Text(stringResource(R.string.activity_review_required), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = actions.acknowledgeReview) { Text(stringResource(R.string.activity_review_ack)) }
                        }
                        OutlinedButton(onClick = actions.refresh, enabled = !state.isScanning, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.isScanning) R.string.scanning else R.string.activity_scan))
                        }
                        OutlinedButton(onClick = { page = ActivityPage.Record }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_record_title)) }
                        OutlinedButton(onClick = { actions.refreshIdentification(); page = ActivityPage.Diagnostics }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_diagnostics_title)) }
                    } }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.activity_search)) }, singleLine = true)
                    }
                    item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActivityListFilter.entries.forEach { option -> FilterChip(filter == option, { filter = option }, label = { Text(stringResource(option.labelRes)) }) }
                    } }
                    item { RuleSwitch(stringResource(R.string.activity_show_disabled), showDisabled) { showDisabled = it } }
                    val rows = activityRows(state.details).filter { row ->
                        val rule = state.details.rules.find { it.activityName == row.canonicalName }
                        val configured = rule?.mode?.let { it != ActivityRuleMode.FollowApp.name } == true
                        val visited = state.details.visits.any { it.activityName == row.canonicalName }
                        (showDisabled || (row.isPresent && row.isEnabled) || configured) &&
                            (filter != ActivityListFilter.Configured || configured) && (filter != ActivityListFilter.Visited || visited) &&
                            (query.isBlank() || row.className.contains(query, true) || rule?.note?.contains(query, true) == true)
                    }
                    if (rows.isEmpty()) item { Text(stringResource(R.string.activity_empty)) }
                    items(rows, key = { it.className }) { row -> ActivityComponentCard(row, state.details) { selectedClass = row.canonicalName } }
                }
                ActivityPage.Record -> {
                    item { Text(stringResource(R.string.activity_record_help)) }
                    item { Text(stringResource(R.string.activity_record_same), style = MaterialTheme.typography.bodySmall) }
                    item { ActivityPermissionButtons(state, system) }
                    item {
                        val session = state.recording
                        if (session.active) {
                            Text(stringResource(R.string.activity_record_active))
                            OutlinedButton(onClick = actions.stopRecording, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_record_stop)) }
                        } else {
                            if (session.sessionId != null && session.packageName == pkg) Text(stringResource(R.string.activity_record_finished))
                            Button(onClick = { actions.startRecording(true) }, enabled = state.canRecord && state.app != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_record_start)) }
                            OutlinedButton(onClick = { actions.startRecording(false) }, enabled = state.canRecord && state.app != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_record_manual)) }
                        }
                        if (session.needsManualLaunch) Text(stringResource(R.string.activity_record_manual_help))
                    }
                    val entries = state.details.visits.filter { it.sessionId != null && it.sessionId == state.recording.sessionId }
                    if (entries.isEmpty()) item { Text(stringResource(R.string.activity_record_empty)) }
                    items(entries, key = { it.activityName }) { visit ->
                        Card(onClick = { selectedClass = visit.activityName }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(visit.activityName.substringAfterLast('.'), fontWeight = FontWeight.SemiBold)
                                ClassText(visit.activityName)
                                Text(stringResource(R.string.activity_last_entry, activityTime(visit.firstEnteredAtMillis)))
                            }
                        }
                    }
                }
                ActivityPage.Diagnostics -> {
                    item { ActivityPermissionButtons(state, system) }
                    item { Text(stringResource(R.string.activity_monitor_mode, state.monitorMode.label)) }
                    item { SettingsSection(stringResource(R.string.activity_recent_confirmation)) {
                        if (!state.automationActive) Text(stringResource(R.string.activity_global_pause))
                        val info = state.foreground
                        ClassText(info?.packageName ?: stringResource(R.string.unknown_app))
                        ClassText(info?.confirmedActivity() ?: stringResource(R.string.activity_unknown))
                        Text(stringResource(R.string.activity_confirmation_time, activityTime(info?.eventTimestampMillis ?: 0)))
                        Text(stringResource(R.string.activity_confirmation_source, info?.source?.label ?: stringResource(R.string.activity_source_unknown)))
                        if (info?.packageName == pkg) {
                            val defaultRule = state.app?.let { AppRuntimeRuleInfo(it.isInOnlineList, it.isInBlacklist, it.sourceTag()) }
                            val rules = state.details.rules.filter { it.mode != ActivityRuleMode.FollowApp.name }.map { rule -> ActivityRuntimeRule(pkg, rule.activityName, rule.mode, state.details.config?.rulesEnabled != false,
                                state.app != null && state.details.config?.scanError == null && state.details.components.any { it.className == rule.activityName && it.isPresent && it.isEnabled && it.isDeclared }) }
                            Text(resolveActivityRule(info, defaultRule, rules).reason.label)
                        } else Text(stringResource(R.string.activity_other_app))
                        Text(stringResource(R.string.activity_cached_help), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = actions.refreshIdentification, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_refresh_identification)) }
                    } }
                }
            }
        }
    }
}

internal fun activityRows(details: ActivityDetails): List<ActivityComponentEntity> {
    val known = details.components.map { it.className }.toSet()
    return (details.components + details.rules.filter { it.activityName !in known }.map {
        ActivityComponentEntity(it.packageName, it.activityName, isEnabled = false, isExported = false, isPresent = false,
            versionCode = 0, scannedAtMillis = 0)
    }).sortedBy { it.className }
}

@Composable
private fun ActivityComponentCard(component: ActivityComponentEntity, details: ActivityDetails, open: () -> Unit) {
    val rule = details.rules.find { it.activityName == component.canonicalName }
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(rule?.note?.takeIf { it.isNotBlank() } ?: component.className.substringAfterLast('.'), fontWeight = FontWeight.SemiBold)
            ClassText(component.className)
            component.targetActivity?.let { Text(stringResource(R.string.activity_alias_target, it), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(ActivityRuleMode.from(rule?.mode.orEmpty()).labelRes))
            if (!component.isPresent || !component.isEnabled) Text(stringResource(R.string.activity_unavailable), color = MaterialTheme.colorScheme.error)
            if (!component.isDeclared) Text(stringResource(R.string.activity_runtime_discovered))
            val visited = details.visits.any { it.activityName == component.canonicalName }
            Text(stringResource(if (!visited) R.string.activity_not_visited else if (component.targetActivity != null) R.string.activity_target_visited else R.string.activity_visited), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActivityPermissionButtons(state: ActivityManagementState, system: SystemIntentActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_permissions,
            stringResource(if (state.usageGranted) R.string.enabled else R.string.disabled),
            stringResource(if (state.accessibilityGranted) R.string.enabled else R.string.disabled)))
        if (!state.canRecord || state.recording.authorizationMissing) Text(stringResource(R.string.activity_record_permission), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = system.openUsageAccessSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_usage_settings)) }
        OutlinedButton(onClick = system.openAccessibilitySettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.activity_accessibility_settings)) }
    }
}

@Composable
internal fun ClassText(value: String) { SelectionContainer { Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityTopBar(title: String, back: () -> Unit) {
    CenterAlignedTopAppBar(title = { Text(title) }, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
    })
}

@Composable
private fun RuleRadio(label: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, onClick = choose, role = Role.RadioButton).padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RuleSwitch(label: String, enabled: Boolean, toggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp))
        Switch(enabled, toggle, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun activityTime(timestamp: Long): String = if (timestamp <= 0) stringResource(R.string.unknown)
    else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityEditorScreen(state: ActivityManagementState, className: String, actions: ActivityActions, finished: () -> Unit) {
    val pkg = state.packageName ?: return
    val rule = state.details.rules.find { it.activityName == className }
    val initialMode = remember(pkg, className) { ActivityRuleMode.from(rule?.mode.orEmpty()) }
    val initialNote = remember(pkg, className) { rule?.note.orEmpty() }
    var mode by rememberSaveable(pkg, className) { mutableStateOf(initialMode) }
    var note by rememberSaveable(pkg, className) { mutableStateOf(initialNote) }
    var confirmDiscard by rememberSaveable(pkg, className) { mutableStateOf(false) }
    val openedAtSequence = rememberSaveable(pkg, className) { state.savedSequence }
    LaunchedEffect(state.savedSequence) { if (state.savedSequence > openedAtSequence) finished() }
    val back: () -> Unit = {
        if (!state.isSaving) {
            if (mode != initialMode || note != initialNote) confirmDiscard = true else finished()
        }
    }
    BackHandler(onBack = back)
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text(stringResource(R.string.activity_unsaved_title)) },
        text = { Text(stringResource(R.string.activity_unsaved_message)) },
        confirmButton = { TextButton(onClick = finished) { Text(stringResource(R.string.activity_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.activity_continue_editing)) } },
    )
    Scaffold(topBar = { ActivityTopBar(stringResource(R.string.activity_editor_title), back) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(state.app?.label ?: pkg, style = MaterialTheme.typography.titleMedium)
                ClassText(pkg)
                Text(className.substringAfterLast('.'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ClassText(className)
            }
            item {
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.activity_note)) }, maxLines = 4)
                Text(stringResource(R.string.activity_note_help), style = MaterialTheme.typography.bodySmall)
            }
            item { SettingsSection(stringResource(R.string.activity_editor_title)) {
                ActivityRuleMode.entries.forEach { option -> RuleRadio(stringResource(option.labelRes), mode == option) { mode = option } }
                Text(stringResource(R.string.activity_override_help), style = MaterialTheme.typography.bodySmall)
                val component = state.details.components.find { it.className == className }
                val valid = component?.let { it.isPresent && it.isEnabled && it.isDeclared } == true && state.app != null && state.details.config?.scanError == null
                val enabled = state.details.config?.rulesEnabled != false
                val online = if (valid && enabled && mode != ActivityRuleMode.FollowApp) mode == ActivityRuleMode.Online else state.app?.isInOnlineList == true
                Text(stringResource(if (online) R.string.activity_effective_online else R.string.activity_effective_offline), fontWeight = FontWeight.SemiBold)
                if (!enabled) Text(stringResource(R.string.activity_paused_description))
                if (!valid) Text(stringResource(R.string.activity_unavailable), color = MaterialTheme.colorScheme.error)
                if (component?.isDeclared == false) {
                    Text(stringResource(R.string.activity_runtime_verify))
                    OutlinedButton(onClick = actions.refresh, enabled = !state.isScanning) { Text(stringResource(R.string.activity_scan)) }
                }
            } }
            item {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { actions.save(className, mode, note) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.activity_save))
                }
            }
        }
    }
}
