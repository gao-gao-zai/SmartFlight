package com.gaozay.smartflight

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.update.UpdateCheckResult
import com.gaozay.smartflight.update.UpdateRepository
import com.gaozay.smartflight.update.UpdateUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModelActionDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val automationServiceController: AutomationServiceController,
    private val accessActions: AccessActions,
    private val automationSettingsActions: AutomationSettingsActions,
    private val appsManagementController: AppsManagementController,
    private val updateRepository: UpdateRepository,
) {
    private lateinit var scope: CoroutineScope
    private val mutableUpdateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    val updateUiState: StateFlow<UpdateUiState> = mutableUpdateUiState

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

    fun setForegroundMonitorMode(mode: ForegroundMonitorMode) {
        updateSettings { it.copy(foregroundMonitorMode = mode) }
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

    fun checkForUpdates(manual: Boolean) {
        if (mutableUpdateUiState.value is UpdateUiState.Checking) {
            return
        }
        scope.launch {
            mutableUpdateUiState.value = UpdateUiState.Checking(manual)
            val settings = settingsRepository.settings.first()
            when (
                val result = updateRepository.checkForUpdates(
                    currentVersion = BuildConfig.VERSION_NAME,
                    skippedUpdateVersion = settings.skippedUpdateVersion,
                    manual = manual,
                )
            ) {
                is UpdateCheckResult.UpdateAvailable -> {
                    mutableUpdateUiState.value = UpdateUiState.UpdateAvailable(
                        release = result.release,
                        manual = manual,
                    )
                }
                is UpdateCheckResult.UpToDate -> {
                    mutableUpdateUiState.value = if (manual) {
                        UpdateUiState.UpToDate("当前已是最新版本")
                    } else {
                        UpdateUiState.Idle
                    }
                }
                is UpdateCheckResult.Skipped -> {
                    mutableUpdateUiState.value = UpdateUiState.Idle
                }
                is UpdateCheckResult.Failed -> {
                    mutableUpdateUiState.value = if (manual) {
                        UpdateUiState.Failed("检查更新失败：${result.message}")
                    } else {
                        UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun dismissUpdatePrompt() {
        mutableUpdateUiState.value = UpdateUiState.Idle
    }

    fun skipUpdateVersion(tag: String) {
        scope.launch {
            settingsRepository.updateSettings { it.copy(skippedUpdateVersion = tag) }
            mutableUpdateUiState.value = UpdateUiState.Idle
        }
    }

    fun copyUpdateLink(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SmartFlight 更新链接", url))
    }

    fun openUpdateLink(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
