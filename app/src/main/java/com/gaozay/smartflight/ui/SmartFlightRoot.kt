package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings

private enum class SmartFlightScreen(val title: String) {
    Dashboard("控制台"), Apps("应用范围"), Rules("自动化规则"), Diagnostics("诊断与日志"), Appearance("外观设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlightRoot(
    state: SmartFlightUiState,
    appsState: AppsUiState,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onSetNetworkControlMode: (NetworkControlMode) -> Unit,
    onSetPreferredExecutorType: (ExecutorType) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetThemePalette: (ThemePalette) -> Unit,
    onSetCustomSeedColor: (Int) -> Unit,
    onSetThemeIntensity: (ThemeIntensity) -> Unit,
    onSetCornerStyle: (CornerStyle) -> Unit,
    onSetAutomationEnabled: (Boolean) -> Unit,
    onDisableAutomation: (AutomationDisableMode) -> Unit,
    onSetMonitorForegroundWhenScreenOff: (Boolean) -> Unit,
    onAppQueryChange: (String) -> Unit,
    onAppFilterChange: (AppFilter) -> Unit,
    onAppInternetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    onAppTypeFilterChange: (AppTypeFilter) -> Unit,
    onAppLauncherFilterChange: (LauncherFilter) -> Unit,
    onClearAppAdvancedFilters: () -> Unit,
    onRefreshApps: () -> Unit,
    onSetAppManualOnline: (String) -> Unit,
    onSetAppManualOffline: (String) -> Unit,
    onResetAppToDefault: (String) -> Unit,
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
    onAutoGrantCompanionPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(SmartFlightScreen.Dashboard) }
    if (!state.accessGateState.canEnterApp) {
        Scaffold(topBar = { SmartFlightTopBar("SmartFlight 接入检查") }) { innerPadding ->
            Surface(Modifier.fillMaxSize().padding(innerPadding)) {
                AccessGateScreen(
                    state = state.accessGateState,
                    onRefresh = onRefreshAccessChecks,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onProbeRootAccess = onProbeRootAccess,
                    onSetAdbBootstrapped = onSetAdbBootstrapped,
                    onAutoGrantCompanionPermissions = onAutoGrantCompanionPermissions,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                )
            }
        }
        return
    }
    Scaffold(
        topBar = {
            if (screen == SmartFlightScreen.Dashboard) {
                DashboardTopBar { screen = SmartFlightScreen.Appearance }
            } else {
                SmartFlightTopBar(screen.title) { screen = SmartFlightScreen.Dashboard }
            }
        },
    ) { innerPadding ->
        Surface(Modifier.fillMaxSize()) {
            when (screen) {
                SmartFlightScreen.Dashboard -> DashboardScreen(
                    state = state,
                    innerPadding = innerPadding,
                    onSetAutomationEnabled = onSetAutomationEnabled,
                    onDisableAutomation = onDisableAutomation,
                    onOpenApps = { screen = SmartFlightScreen.Apps },
                    onOpenRules = { screen = SmartFlightScreen.Rules },
                    onOpenDiagnostics = { screen = SmartFlightScreen.Diagnostics },
                )
                SmartFlightScreen.Apps -> AppManagementScreen(
                    state = appsState,
                    innerPadding = innerPadding,
                    onQueryChange = onAppQueryChange,
                    onFilterChange = onAppFilterChange,
                    onInternetPermissionFilterChange = onAppInternetPermissionFilterChange,
                    onAppTypeFilterChange = onAppTypeFilterChange,
                    onLauncherFilterChange = onAppLauncherFilterChange,
                    onClearAdvancedFilters = onClearAppAdvancedFilters,
                    onRefreshApps = onRefreshApps,
                    onSetManualOnline = onSetAppManualOnline,
                    onSetManualOffline = onSetAppManualOffline,
                    onResetToDefault = onResetAppToDefault,
                )
                SmartFlightScreen.Rules -> RulesScreen(
                    settings = state.settings,
                    innerPadding = innerPadding,
                    onUpdateSettings = onUpdateSettings,
                    onSetNetworkControlMode = onSetNetworkControlMode,
                    onSetPreferredExecutorType = onSetPreferredExecutorType,
                    onSetMonitorForegroundWhenScreenOff = onSetMonitorForegroundWhenScreenOff,
                )
                SmartFlightScreen.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    innerPadding = innerPadding,
                    onRefreshAccessChecks = onRefreshAccessChecks,
                    onProbeCurrentNetworkControlState = onProbeCurrentNetworkControlState,
                    onToggleCurrentNetworkControlState = onToggleCurrentNetworkControlState,
                    onSimulateScreenOff = onSimulateScreenOff,
                    onSimulateScreenOn = onSimulateScreenOn,
                    onClearExecutionLogs = onClearExecutionLogs,
                    onRequestBluetoothPermission = onRequestBluetoothPermission,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onProbeRootAccess = onProbeRootAccess,
                    onSetAdbBootstrapped = onSetAdbBootstrapped,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                )
                SmartFlightScreen.Appearance -> AppearanceScreen(
                    settings = state.settings,
                    innerPadding = innerPadding,
                    onSetThemeMode = onSetThemeMode,
                    onSetThemePalette = onSetThemePalette,
                    onSetCustomSeedColor = onSetCustomSeedColor,
                    onSetThemeIntensity = onSetThemeIntensity,
                    onSetCornerStyle = onSetCornerStyle,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(onOpenAppearance: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("自动飞行", fontWeight = FontWeight.Bold)
                Text("SmartFlight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("外观设置") },
                        leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                        onClick = { expanded = false; onOpenAppearance() },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartFlightTopBar(title: String, onBack: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        },
    )
}
