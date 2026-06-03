package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.update.GITEE_RELEASES_URL
import com.gaozay.smartflight.update.UpdateUiState

private enum class SmartFlightScreen(val title: String) {
    Dashboard("控制台"), Apps("应用范围"), Rules("自动化规则"), Diagnostics("诊断与日志"), Appearance("外观设置"), About("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlightRoot(
    state: SmartFlightUiState,
    appsState: AppsUiState,
    updateState: UpdateUiState,
    actions: SmartFlightActions,
) {
    var screen by rememberSaveable { mutableStateOf(SmartFlightScreen.Dashboard) }
    UpdatePromptDialog(
        state = updateState,
        onDismiss = actions.update.dismissUpdatePrompt,
        onCopyLink = actions.update.copyUpdateLink,
        onOpenLink = actions.update.openUpdateLink,
        onSkipVersion = actions.update.skipUpdateVersion,
    )
    if (!state.accessGateState.canEnterApp) {
        Scaffold(topBar = { SmartFlightTopBar("SmartFlight 接入检查") }) { innerPadding ->
            Surface(Modifier.fillMaxSize().padding(innerPadding)) {
                AccessGateScreen(
                    state = state.accessGateState,
                    onRefresh = actions.access.refreshAccessChecks,
                    onRequestShizukuPermission = actions.access.requestShizukuPermission,
                    onProbeRootAccess = actions.access.probeRootAccess,
                    onSetAdbBootstrapped = actions.access.setAdbBootstrapped,
                    onAutoGrantCompanionPermissions = actions.access.autoGrantCompanionPermissions,
                    onOpenUsageAccessSettings = actions.system.openUsageAccessSettings,
                    onOpenAccessibilitySettings = actions.system.openAccessibilitySettings,
                    onOpenNotificationSettings = actions.system.openNotificationSettings,
                    onOpenBatteryOptimizationSettings = actions.system.openBatteryOptimizationSettings,
                )
            }
        }
        return
    }
    Scaffold(
        topBar = {
            if (screen == SmartFlightScreen.Dashboard) {
                DashboardTopBar()
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
                    onSetAutomationEnabled = actions.automation.setAutomationEnabled,
                    onDisableAutomation = actions.automation.disableAutomation,
                    onOpenApps = { screen = SmartFlightScreen.Apps },
                    onOpenRules = { screen = SmartFlightScreen.Rules },
                    onOpenDiagnostics = { screen = SmartFlightScreen.Diagnostics },
                    onOpenAppearance = { screen = SmartFlightScreen.Appearance },
                    onOpenAbout = { screen = SmartFlightScreen.About },
                )
                SmartFlightScreen.Apps -> AppManagementScreen(
                    state = appsState,
                    innerPadding = innerPadding,
                    onQueryChange = actions.apps.queryChange,
                    onFilterChange = actions.apps.filterChange,
                    onInternetPermissionFilterChange = actions.apps.internetPermissionFilterChange,
                    onAppTypeFilterChange = actions.apps.typeFilterChange,
                    onLauncherFilterChange = actions.apps.launcherFilterChange,
                    onClearAdvancedFilters = actions.apps.clearAdvancedFilters,
                    onRefreshApps = actions.apps.refreshApps,
                    onSetManualOnline = actions.apps.setManualOnline,
                    onSetManualOffline = actions.apps.setManualOffline,
                    onResetToDefault = actions.apps.resetToDefault,
                )
                SmartFlightScreen.Rules -> RulesScreen(
                    settings = state.settings,
                    innerPadding = innerPadding,
                    onUpdateSettings = actions.settings.updateSettings,
                    onSetNetworkControlMode = actions.settings.setNetworkControlMode,
                    onSetPreferredExecutorType = actions.settings.setPreferredExecutorType,
                    onSetForegroundMonitorMode = actions.settings.setForegroundMonitorMode,
                    onSetMonitorForegroundWhenScreenOff = actions.settings.setMonitorForegroundWhenScreenOff,
                )
                SmartFlightScreen.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    innerPadding = innerPadding,
                    onRefreshAccessChecks = actions.access.refreshAccessChecks,
                    onProbeCurrentNetworkControlState = actions.diagnostics.probeCurrentNetworkControlState,
                    onToggleCurrentNetworkControlState = actions.diagnostics.toggleCurrentNetworkControlState,
                    onSimulateScreenOff = actions.diagnostics.simulateScreenOff,
                    onSimulateScreenOn = actions.diagnostics.simulateScreenOn,
                    onClearExecutionLogs = actions.diagnostics.clearExecutionLogs,
                    onRequestBluetoothPermission = actions.diagnostics.requestBluetoothPermission,
                    onRequestShizukuPermission = actions.access.requestShizukuPermission,
                    onProbeRootAccess = actions.access.probeRootAccess,
                    onSetAdbBootstrapped = actions.access.setAdbBootstrapped,
                    onOpenUsageAccessSettings = actions.system.openUsageAccessSettings,
                    onOpenAccessibilitySettings = actions.system.openAccessibilitySettings,
                    onOpenNotificationSettings = actions.system.openNotificationSettings,
                    onOpenBatteryOptimizationSettings = actions.system.openBatteryOptimizationSettings,
                )
                SmartFlightScreen.Appearance -> AppearanceScreen(
                    settings = state.settings,
                    innerPadding = innerPadding,
                    onSetThemeMode = actions.settings.setThemeMode,
                    onSetThemePalette = actions.settings.setThemePalette,
                    onSetCustomSeedColor = actions.settings.setCustomSeedColor,
                    onSetThemeIntensity = actions.settings.setThemeIntensity,
                    onSetCornerStyle = actions.settings.setCornerStyle,
                )
                SmartFlightScreen.About -> AboutScreen(
                    updateState = updateState,
                    innerPadding = innerPadding,
                    onCheckForUpdates = { actions.update.checkForUpdates(true) },
                    onOpenReleasePage = { actions.update.openUpdateLink(GITEE_RELEASES_URL) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("自动飞行", fontWeight = FontWeight.Bold)
                Text("SmartFlight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
