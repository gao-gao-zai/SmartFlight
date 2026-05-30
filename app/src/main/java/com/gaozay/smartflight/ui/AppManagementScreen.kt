package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter

@Composable
fun AppManagementScreen(
    state: AppsUiState,
    innerPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onInternetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    onAppTypeFilterChange: (AppTypeFilter) -> Unit,
    onLauncherFilterChange: (LauncherFilter) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onRefreshApps: () -> Unit,
    onSetManualOnline: (String) -> Unit,
    onSetManualOffline: (String) -> Unit,
    onResetToDefault: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AppScopeSummary(state = state, onRefreshApps = onRefreshApps) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索应用名称或包名") },
            )
        }
        item {
            FilterSummaryRow(
                state = state,
                onFilterChange = onFilterChange,
                onInternetPermissionFilterChange = onInternetPermissionFilterChange,
                onAppTypeFilterChange = onAppTypeFilterChange,
                onLauncherFilterChange = onLauncherFilterChange,
                onClearAdvancedFilters = onClearAdvancedFilters,
            )
        }
        item {
            Text(
                text = "应用列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.apps.isEmpty()) {
            item { EmptyAppsCard(onRefreshApps = onRefreshApps) }
        } else {
            items(
                items = state.apps,
                key = { it.packageName },
            ) { app ->
                AppRow(
                    app = app,
                    onSetManualOnline = onSetManualOnline,
                    onSetManualOffline = onSetManualOffline,
                    onResetToDefault = onResetToDefault,
                )
            }
        }
    }
}
