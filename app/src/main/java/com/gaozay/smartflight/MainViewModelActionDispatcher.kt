package com.gaozay.smartflight

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
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.runtime.AutomationServiceController
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModelActionDispatcher @Inject constructor(
    private val executionLogRepository: ExecutionLogRepository,
    private val automationServiceController: AutomationServiceController,
    private val accessActions: AccessActions,
    private val automationSettingsActions: AutomationSettingsActions,
    private val appsManagementController: AppsManagementController,
) {
    private lateinit var scope: CoroutineScope

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun refreshAccessChecks() {
        scope.launch {
            accessActions.refreshAccessChecks()
        }
    }

    fun setAdbBootstrapped(bootstrapped: Boolean) {
        scope.launch {
            accessActions.setAdbBootstrapped(bootstrapped)
        }
    }

    fun probeRootAccess() {
        scope.launch {
            accessActions.probeRootAccess()
        }
    }

    fun autoGrantCompanionPermissions() {
        scope.launch {
            accessActions.autoGrantCompanionPermissions()
        }
    }

    fun syncCurrentNetworkControlState() {
        scope.launch {
            accessActions.syncCurrentNetworkControlState()
        }
    }

    fun probeCurrentNetworkControlState() {
        scope.launch {
            accessActions.probeCurrentNetworkControlState()
        }
    }

    fun toggleCurrentNetworkControlState() {
        scope.launch {
            accessActions.toggleCurrentNetworkControlState()
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        scope.launch {
            automationSettingsActions.setAutomationEnabled(enabled)
        }
    }

    fun disableAutomation(mode: AutomationDisableMode) {
        scope.launch {
            automationSettingsActions.disableAutomation(mode)
        }
    }

    fun clearExecutionLogs() {
        scope.launch {
            executionLogRepository.clearLogs()
        }
    }

    fun setMonitorForegroundWhenScreenOff(enabled: Boolean) {
        scope.launch {
            automationSettingsActions.setMonitorForegroundWhenScreenOff(enabled)
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        scope.launch {
            automationSettingsActions.updateSettings(transform)
        }
    }

    fun setNetworkControlMode(mode: NetworkControlMode) {
        scope.launch {
            automationSettingsActions.setNetworkControlMode(mode)
        }
    }

    fun setPreferredExecutorType(type: ExecutorType) {
        scope.launch {
            automationSettingsActions.setPreferredExecutorType(type)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        updateSettings { it.copy(themeMode = mode) }
    }

    fun setThemePalette(palette: ThemePalette) {
        updateSettings { it.copy(themePalette = palette) }
    }

    fun setCustomSeedColor(seedColorArgb: Int) {
        updateSettings {
            it.copy(
                themePalette = ThemePalette.Custom,
                customSeedColorArgb = seedColorArgb,
            )
        }
    }

    fun setThemeIntensity(intensity: ThemeIntensity) {
        updateSettings { it.copy(themeIntensity = intensity) }
    }

    fun setCornerStyle(cornerStyle: CornerStyle) {
        updateSettings { it.copy(cornerStyle = cornerStyle) }
    }

    fun simulateScreenOff() {
        automationServiceController.simulateScreenOff()
    }

    fun simulateScreenOn() {
        automationServiceController.simulateScreenOn()
    }

    fun updateAppQuery(query: String) {
        appsManagementController.updateAppQuery(query)
    }

    fun updateAppFilter(filter: AppFilter) {
        appsManagementController.updateAppFilter(filter)
    }

    fun updateAppInternetPermissionFilter(filter: InternetPermissionFilter) {
        appsManagementController.updateAppInternetPermissionFilter(filter)
    }

    fun updateAppTypeFilter(filter: AppTypeFilter) {
        appsManagementController.updateAppTypeFilter(filter)
    }

    fun updateAppLauncherFilter(filter: LauncherFilter) {
        appsManagementController.updateAppLauncherFilter(filter)
    }

    fun clearAppAdvancedFilters() {
        appsManagementController.clearAppAdvancedFilters()
    }

    fun refreshInstalledApps() {
        scope.launch {
            appsManagementController.refreshInstalledApps()
        }
    }

    fun setAppManualOnline(packageName: String) {
        scope.launch {
            appsManagementController.setAppManualOnline(packageName)
        }
    }

    fun setAppManualOffline(packageName: String) {
        scope.launch {
            appsManagementController.setAppManualOffline(packageName)
        }
    }

    fun resetAppToDefault(packageName: String) {
        scope.launch {
            appsManagementController.resetAppToDefault(packageName)
        }
    }
}
