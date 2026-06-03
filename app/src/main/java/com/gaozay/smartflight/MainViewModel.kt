package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.runtime.AutomationServiceController
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.update.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val automationServiceController: AutomationServiceController,
    private val uiStateAssembler: MainUiStateAssembler,
    private val actionDispatcher: MainViewModelActionDispatcher,
) : ViewModel() {
    val uiState: StateFlow<SmartFlightUiState> = uiStateAssembler.smartFlightUiState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartFlightUiState(),
    )

    val appsUiState: StateFlow<AppsUiState> = uiStateAssembler.appsUiState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppsUiState(),
    )

    val updateUiState: StateFlow<UpdateUiState> = actionDispatcher.updateUiState

    init {
        actionDispatcher.attach(viewModelScope)
        checkForUpdates(manual = false)
        refreshAccessChecks()
        observeAutomationState()
    }

    fun refreshAccessChecks() = actionDispatcher.refreshAccessChecks()

    fun setAdbBootstrapped(bootstrapped: Boolean) = actionDispatcher.setAdbBootstrapped(bootstrapped)

    fun probeRootAccess() = actionDispatcher.probeRootAccess()

    fun autoGrantCompanionPermissions() = actionDispatcher.autoGrantCompanionPermissions()

    fun syncCurrentNetworkControlState() = actionDispatcher.syncCurrentNetworkControlState()

    fun probeCurrentNetworkControlState() = actionDispatcher.probeCurrentNetworkControlState()

    fun toggleCurrentNetworkControlState() = actionDispatcher.toggleCurrentNetworkControlState()

    fun setAutomationEnabled(enabled: Boolean) = actionDispatcher.setAutomationEnabled(enabled)

    fun disableAutomation(mode: AutomationDisableMode) = actionDispatcher.disableAutomation(mode)

    fun clearExecutionLogs() = actionDispatcher.clearExecutionLogs()

    fun setMonitorForegroundWhenScreenOff(enabled: Boolean) =
        actionDispatcher.setMonitorForegroundWhenScreenOff(enabled)

    fun updateSettings(transform: (UserSettings) -> UserSettings) = actionDispatcher.updateSettings(transform)

    fun setNetworkControlMode(mode: NetworkControlMode) = actionDispatcher.setNetworkControlMode(mode)

    fun setPreferredExecutorType(type: ExecutorType) = actionDispatcher.setPreferredExecutorType(type)

    fun setForegroundMonitorMode(mode: ForegroundMonitorMode) = actionDispatcher.setForegroundMonitorMode(mode)

    fun setThemeMode(mode: ThemeMode) = actionDispatcher.setThemeMode(mode)

    fun setThemePalette(palette: ThemePalette) = actionDispatcher.setThemePalette(palette)

    fun setCustomSeedColor(seedColorArgb: Int) = actionDispatcher.setCustomSeedColor(seedColorArgb)

    fun setThemeIntensity(intensity: ThemeIntensity) = actionDispatcher.setThemeIntensity(intensity)

    fun setCornerStyle(cornerStyle: CornerStyle) = actionDispatcher.setCornerStyle(cornerStyle)

    fun simulateScreenOff() = actionDispatcher.simulateScreenOff()

    fun simulateScreenOn() = actionDispatcher.simulateScreenOn()

    fun updateAppQuery(query: String) = actionDispatcher.updateAppQuery(query)

    fun updateAppFilter(filter: AppFilter) = actionDispatcher.updateAppFilter(filter)

    fun updateAppInternetPermissionFilter(filter: InternetPermissionFilter) =
        actionDispatcher.updateAppInternetPermissionFilter(filter)

    fun updateAppTypeFilter(filter: AppTypeFilter) = actionDispatcher.updateAppTypeFilter(filter)

    fun updateAppLauncherFilter(filter: LauncherFilter) = actionDispatcher.updateAppLauncherFilter(filter)

    fun clearAppAdvancedFilters() = actionDispatcher.clearAppAdvancedFilters()

    fun refreshInstalledApps() = actionDispatcher.refreshInstalledApps()

    fun setAppManualOnline(packageName: String) = actionDispatcher.setAppManualOnline(packageName)

    fun setAppManualOffline(packageName: String) = actionDispatcher.setAppManualOffline(packageName)

    fun resetAppToDefault(packageName: String) = actionDispatcher.resetAppToDefault(packageName)

    fun checkForUpdates(manual: Boolean) = actionDispatcher.checkForUpdates(manual)

    fun dismissUpdatePrompt() = actionDispatcher.dismissUpdatePrompt()

    fun skipUpdateVersion(tag: String) = actionDispatcher.skipUpdateVersion(tag)

    fun copyUpdateLink(url: String) = actionDispatcher.copyUpdateLink(url)

    fun openUpdateLink(url: String) = actionDispatcher.openUpdateLink(url)

    private fun observeAutomationState() {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.automationEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    automationServiceController.setAutomationEnabled(enabled)
                }
        }
    }

}

@Immutable
data class SmartFlightUiState(
    val accessGateState: AccessGateState = AccessGateState(),
    val settings: UserSettings = UserSettings(),
    val advancedAccess: String = "需要 Shizuku / ADB / Root",
    val currentMode: String = "飞行模式",
    val automationEnabled: Boolean = false,
    val automationDisabled: Boolean = true,
    val automationDisableSummary: String? = "已永久禁用",
    val monitorForegroundWhenScreenOff: Boolean = false,
    val foregroundApp: String = "尚未连接",
    val runtimeExecutor: String = "不可用",
    val runtimeLastCheck: String = "尚未执行自检",
    val runtimeLastResult: String = ExecutionResult.Pending.label,
    val runtimeUpdatedAtMillis: Long = 0,
    val unifiedNetworkState: String = "Unknown",
    val wifiStatus: String = "未知",
    val bluetoothStatus: String = "未知",
    val mobileDataStatus: String = "未知",
    val bluetoothReadable: Boolean = false,
    val executorDiagnostics: List<ExecutorDiagnosticItem> = emptyList(),
    val recentExecutionLogs: List<ExecutionLogItem> = emptyList(),
    val triggerSummary: String = "项目已初始化，运行时引擎待接入。",
)

@Immutable
data class ExecutorDiagnosticItem(
    val executor: String,
    val summary: String,
    val detail: String,
    val command: String,
    val output: String,
    val ready: Boolean,
)

@Immutable
data class ExecutionLogItem(
    val timestampMillis: Long,
    val action: String,
    val executor: String,
    val result: String,
    val detail: String,
)
