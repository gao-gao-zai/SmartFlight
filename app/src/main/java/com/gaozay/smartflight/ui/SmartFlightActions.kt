package com.gaozay.smartflight.ui

import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
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

data class SmartFlightActions(
    val settings: SettingsActions,
    val automation: AutomationActions,
    val apps: AppsActions,
    val access: AccessActions,
    val diagnostics: DiagnosticsActions,
    val system: SystemIntentActions,
    val update: UpdateActions,
)

data class SettingsActions(
    val updateSettings: ((UserSettings) -> UserSettings) -> Unit,
    val setNetworkControlMode: (NetworkControlMode) -> Unit,
    val setPreferredExecutorType: (ExecutorType) -> Unit,
    val setMonitorForegroundWhenScreenOff: (Boolean) -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
    val setThemePalette: (ThemePalette) -> Unit,
    val setCustomSeedColor: (Int) -> Unit,
    val setThemeIntensity: (ThemeIntensity) -> Unit,
    val setCornerStyle: (CornerStyle) -> Unit,
)

data class AutomationActions(
    val setAutomationEnabled: (Boolean) -> Unit,
    val disableAutomation: (AutomationDisableMode) -> Unit,
)

data class AppsActions(
    val queryChange: (String) -> Unit,
    val filterChange: (AppFilter) -> Unit,
    val internetPermissionFilterChange: (InternetPermissionFilter) -> Unit,
    val typeFilterChange: (AppTypeFilter) -> Unit,
    val launcherFilterChange: (LauncherFilter) -> Unit,
    val clearAdvancedFilters: () -> Unit,
    val refreshApps: () -> Unit,
    val setManualOnline: (String) -> Unit,
    val setManualOffline: (String) -> Unit,
    val resetToDefault: (String) -> Unit,
)

data class AccessActions(
    val refreshAccessChecks: () -> Unit,
    val requestShizukuPermission: () -> Unit,
    val probeRootAccess: () -> Unit,
    val setAdbBootstrapped: (Boolean) -> Unit,
    val autoGrantCompanionPermissions: () -> Unit,
)

data class DiagnosticsActions(
    val probeCurrentNetworkControlState: () -> Unit,
    val toggleCurrentNetworkControlState: () -> Unit,
    val simulateScreenOff: () -> Unit,
    val simulateScreenOn: () -> Unit,
    val clearExecutionLogs: () -> Unit,
    val requestBluetoothPermission: () -> Unit,
)

data class SystemIntentActions(
    val openUsageAccessSettings: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openBatteryOptimizationSettings: () -> Unit,
)

data class UpdateActions(
    val checkForUpdates: (Boolean) -> Unit,
    val dismissUpdatePrompt: () -> Unit,
    val skipUpdateVersion: (String) -> Unit,
    val copyUpdateLink: (String) -> Unit,
    val openUpdateLink: (String) -> Unit,
)
