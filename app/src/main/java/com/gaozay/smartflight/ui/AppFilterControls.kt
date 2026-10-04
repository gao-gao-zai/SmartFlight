package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter

@Composable
internal fun FilterSummaryRow(
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
                        text = stringResource(R.string.currently_showing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${state.filteredCount} / ${state.totalCount} · ${state.filter.localizedLabel()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                StatusFilterMenu(state = state, onFilterChange = onFilterChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdvancedFilterMenu(
                    label = stringResource(R.string.online),
                    value = state.internetPermissionFilter.localizedLabel(),
                    active = state.internetPermissionFilter != InternetPermissionFilter.All,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    InternetPermissionFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.localizedLabel()) },
                            onClick = {
                                dismiss()
                                onInternetPermissionFilterChange(filter)
                            },
                        )
                    }
                }
                AdvancedFilterMenu(
                    label = stringResource(R.string.type),
                    value = state.appTypeFilter.localizedLabel(),
                    active = state.appTypeFilter != AppTypeFilter.User,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    AppTypeFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.localizedLabel()) },
                            onClick = {
                                dismiss()
                                onAppTypeFilterChange(filter)
                            },
                        )
                    }
                }
                AdvancedFilterMenu(
                    label = stringResource(R.string.launcher),
                    value = state.launcherFilter.localizedLabel(),
                    active = state.launcherFilter != LauncherFilter.All,
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    LauncherFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.localizedLabel()) },
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
                        text = pluralStringResource(R.plurals.advanced_filters_active, (state.activeAdvancedFilterCount).toInt(), state.activeAdvancedFilterCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onClearAdvancedFilters) {
                        Text(stringResource(R.string.clear))
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
            Text(stringResource(R.string.scope))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text("${filter.localizedLabel()} ${state.countFor(filter)}") },
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
private fun AppFilter.localizedLabel(): String = when (this) {
    AppFilter.All -> stringResource(R.string.all_apps)
    AppFilter.Online -> stringResource(R.string.activity_default_online)
    AppFilter.Offline -> stringResource(R.string.activity_default_offline)
    AppFilter.Whitelist -> stringResource(R.string.allowlist)
    AppFilter.Blacklist -> stringResource(R.string.blocklist)
    AppFilter.WithActivities -> stringResource(R.string.activity_filter_rules)
}

@Composable
private fun InternetPermissionFilter.localizedLabel(): String = when (this) {
    InternetPermissionFilter.All -> stringResource(R.string.all)
    InternetPermissionFilter.Declared -> stringResource(R.string.declares_internet_permission)
    InternetPermissionFilter.NotDeclared -> stringResource(R.string.no_internet_permission_declared)
}

@Composable
private fun AppTypeFilter.localizedLabel(): String = when (this) {
    AppTypeFilter.All -> stringResource(R.string.all)
    AppTypeFilter.User -> stringResource(R.string.user_apps)
    AppTypeFilter.System -> stringResource(R.string.system_apps)
}

@Composable
private fun LauncherFilter.localizedLabel(): String = when (this) {
    LauncherFilter.All -> stringResource(R.string.all)
    LauncherFilter.HasLauncher -> stringResource(R.string.has_launcher_entry)
    LauncherFilter.NoLauncher -> stringResource(R.string.no_launcher_entry)
}
