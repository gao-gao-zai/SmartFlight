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
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.runtime.AutomationServiceController
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
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
    private val executionLogRepository: ExecutionLogRepository,
    private val automationServiceController: AutomationServiceController,
    private val uiStateAssembler: MainUiStateAssembler,
    private val accessActions: AccessActions,
    private val automationSettingsActions: AutomationSettingsActions,
    private val appsManagementController: AppsManagementController,
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

    init {
        refreshAccessChecks()
        observeAutomationState()
    }

    fun refreshAccessChecks() {
        viewModelScope.launch {
            accessActions.refreshAccessChecks()
        }
    }

    fun setAdbBootstrapped(bootstrapped: Boolean) {
        viewModelScope.launch {
            accessActions.setAdbBootstrapped(bootstrapped)
        }
    }

    fun probeRootAccess() {
        viewModelScope.launch {
            accessActions.probeRootAccess()
        }
    }

    fun autoGrantCompanionPermissions() {
        viewModelScope.launch {
            accessActions.autoGrantCompanionPermissions()
        }
    }

    fun syncCurrentNetworkControlState() {
        viewModelScope.launch {
            accessActions.syncCurrentNetworkControlState()
        }
    }

    fun probeCurrentNetworkControlState() {
        viewModelScope.launch {
            accessActions.probeCurrentNetworkControlState()
        }
    }

    fun toggleCurrentNetworkControlState() {
        viewModelScope.launch {
            accessActions.toggleCurrentNetworkControlState()
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            automationSettingsActions.setAutomationEnabled(enabled)
        }
    }

    fun disableAutomation(mode: AutomationDisableMode) {
        viewModelScope.launch {
            automationSettingsActions.disableAutomation(mode)
        }
    }

    fun clearExecutionLogs() {
        viewModelScope.launch {
            executionLogRepository.clearLogs()
        }
    }

    fun setMonitorForegroundWhenScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            automationSettingsActions.setMonitorForegroundWhenScreenOff(enabled)
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            automationSettingsActions.updateSettings(transform)
        }
    }

    fun setNetworkControlMode(mode: NetworkControlMode) {
        viewModelScope.launch {
            automationSettingsActions.setNetworkControlMode(mode)
        }
    }

    fun setPreferredExecutorType(type: ExecutorType) {
        viewModelScope.launch {
            automationSettingsActions.setPreferredExecutorType(type)
        }
    }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    fun setThemePalette(palette: ThemePalette) = updateSettings { it.copy(themePalette = palette) }

    fun setCustomSeedColor(seedColorArgb: Int) = updateSettings {
        it.copy(
            themePalette = ThemePalette.Custom,
            customSeedColorArgb = seedColorArgb,
        )
    }

    fun setThemeIntensity(intensity: ThemeIntensity) = updateSettings { it.copy(themeIntensity = intensity) }

    fun setCornerStyle(cornerStyle: CornerStyle) = updateSettings { it.copy(cornerStyle = cornerStyle) }

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
        viewModelScope.launch {
            appsManagementController.refreshInstalledApps()
        }
    }

    fun setAppManualOnline(packageName: String) {
        viewModelScope.launch {
            appsManagementController.setAppManualOnline(packageName)
        }
    }

    fun setAppManualOffline(packageName: String) {
        viewModelScope.launch {
            appsManagementController.setAppManualOffline(packageName)
        }
    }

    fun resetAppToDefault(packageName: String) {
        viewModelScope.launch {
            appsManagementController.resetAppToDefault(packageName)
        }
    }

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
