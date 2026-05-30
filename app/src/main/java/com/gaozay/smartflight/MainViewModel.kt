package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppFilterState
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.buildAppsUiState
import com.gaozay.smartflight.apps.isOnline
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.AutomationServiceController
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.runtime.buildRuntimeSummary
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import com.gaozay.smartflight.settings.temporaryDisableSummary
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val accessRepository: AccessRepository,
    private val automationServiceController: AutomationServiceController,
) : ViewModel() {
    private val appQuery = MutableStateFlow("")
    private val appFilter = MutableStateFlow(AppFilter.All)
    private val appInternetPermissionFilter = MutableStateFlow(InternetPermissionFilter.All)
    private val appTypeFilter = MutableStateFlow(AppTypeFilter.User)
    private val appLauncherFilter = MutableStateFlow(LauncherFilter.All)
    private val appScanning = MutableStateFlow(false)
    private val appLastScanSummary = MutableStateFlow("尚未扫描")
    private val recentLogs = executionLogRepository.observeRecentLogs(limit = 6)

    val uiState: StateFlow<SmartFlightUiState> = combine(
        settingsRepository.settings,
        runtimeStatusRepository.snapshot,
        installedAppRepository.observeAppCount(),
        executionLogRepository.observeLogCount(),
        accessRepository.accessGateState,
    ) { settings, runtimeSnapshot, appCount, logCount, accessGateState ->
        UiStateBase(
            settings = settings,
            runtimeSnapshot = runtimeSnapshot,
            appCount = appCount,
            logCount = logCount,
            accessGateState = accessGateState,
        )
    }.combine(recentLogs) { base, recentLogs ->
        SmartFlightUiState(
            accessGateState = base.accessGateState,
            settings = base.settings,
            advancedAccess = base.accessGateState.advancedAccess.selectedExecutorType.label,
            currentMode = base.settings.networkControlMode.label,
            automationEnabled = base.settings.isAutomationEffectivelyEnabled(),
            automationDisabled = !base.settings.automationEnabled || base.settings.temporaryDisableSummary() != null,
            automationDisableSummary = base.settings.temporaryDisableSummary()
                ?: if (base.settings.automationEnabled) null else "已永久禁用",
            monitorForegroundWhenScreenOff = base.settings.monitorForegroundWhenScreenOff,
            foregroundApp = base.runtimeSnapshot.currentForegroundAppLabel
                ?: base.runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            runtimeExecutor = base.runtimeSnapshot.activeExecutorType.label,
            runtimeLastCheck = base.runtimeSnapshot.runtimeStatusSummary,
            runtimeLastResult = base.runtimeSnapshot.runtimeStatusResult.label,
            runtimeUpdatedAtMillis = base.runtimeSnapshot.updatedAtMillis,
            unifiedNetworkState = base.runtimeSnapshot.unifiedNetworkState.label,
            wifiStatus = buildWifiStatus(base.runtimeSnapshot),
            bluetoothStatus = buildBluetoothStatus(base.runtimeSnapshot),
            mobileDataStatus = buildMobileDataStatus(base.runtimeSnapshot),
            bluetoothReadable = base.runtimeSnapshot.isBluetoothStateReadable,
            executorDiagnostics = emptyList(),
            recentExecutionLogs = recentLogs.map { it.toUiItem() },
            triggerSummary = buildString {
                append(buildRuntimeSummary(base.settings, base.runtimeSnapshot))
                append(" · Apps: ")
                append(base.appCount)
                append(" · Logs: ")
                append(base.logCount)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartFlightUiState(),
    )

    private val appFilterState = combine(
        appFilter.asStateFlow(),
        appInternetPermissionFilter.asStateFlow(),
        appTypeFilter.asStateFlow(),
        appLauncherFilter.asStateFlow(),
    ) { filter, internetPermissionFilter, typeFilter, launcherFilter ->
        AppFilterState(
            filter = filter,
            internetPermissionFilter = internetPermissionFilter,
            typeFilter = typeFilter,
            launcherFilter = launcherFilter,
        )
    }

    val appsUiState: StateFlow<AppsUiState> = combine(
        installedAppRepository.observeApps(),
        appQuery.asStateFlow(),
        appFilterState,
        appScanning.asStateFlow(),
        appLastScanSummary.asStateFlow(),
    ) { apps, query, filterState, isScanning, lastScanSummary ->
        buildAppsUiState(
            apps = apps,
            query = query,
            filterState = filterState,
            isScanning = isScanning,
            lastScanSummary = lastScanSummary,
        )
    }.stateIn(
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
            accessRepository.refresh()
        }
    }

    fun setAdbBootstrapped(bootstrapped: Boolean) {
        viewModelScope.launch {
            accessRepository.setAdbBootstrapped(bootstrapped)
        }
    }

    fun probeRootAccess() {
        viewModelScope.launch {
            accessRepository.probeRootAccess()
        }
    }

    fun autoGrantCompanionPermissions() {
        viewModelScope.launch {
            accessRepository.autoGrantCompanionPermissions()
        }
    }

    fun syncCurrentNetworkControlState() {
        viewModelScope.launch {
            accessRepository.syncCurrentNetworkControlState()
        }
    }

    fun probeCurrentNetworkControlState() {
        viewModelScope.launch {
            accessRepository.probeCurrentNetworkControlState()
        }
    }

    fun toggleCurrentNetworkControlState() {
        viewModelScope.launch {
            accessRepository.toggleCurrentNetworkControlState()
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settingsRepository.updateSettings { it.withAutomationEnabled() }
            } else {
                val foregroundPackageName = runtimeStatusRepository.snapshot.first().currentForegroundPackageName
                settingsRepository.updateSettings {
                    it.withAutomationDisabled(
                        mode = AutomationDisableMode.Permanent,
                        foregroundPackageName = foregroundPackageName,
                    )
                }
            }
        }
    }

    fun disableAutomation(mode: AutomationDisableMode) {
        viewModelScope.launch {
            val foregroundPackageName = runtimeStatusRepository.snapshot.first().currentForegroundPackageName
            settingsRepository.updateSettings {
                it.withAutomationDisabled(
                    mode = mode,
                    foregroundPackageName = foregroundPackageName,
                )
            }
        }
    }

    fun clearExecutionLogs() {
        viewModelScope.launch {
            executionLogRepository.clearLogs()
        }
    }

    fun setMonitorForegroundWhenScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(monitorForegroundWhenScreenOff = enabled)
            }
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }

    fun setNetworkControlMode(mode: NetworkControlMode) = updateSettings { it.copy(networkControlMode = mode) }

    fun setPreferredExecutorType(type: ExecutorType) {
        viewModelScope.launch {
            settingsRepository.updateSettings { current ->
                current.copy(preferredExecutorType = type)
            }
            accessRepository.refresh()
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
        appQuery.value = query
    }

    fun updateAppFilter(filter: AppFilter) {
        appFilter.value = filter
    }

    fun updateAppInternetPermissionFilter(filter: InternetPermissionFilter) {
        appInternetPermissionFilter.value = filter
    }

    fun updateAppTypeFilter(filter: AppTypeFilter) {
        appTypeFilter.value = filter
    }

    fun updateAppLauncherFilter(filter: LauncherFilter) {
        appLauncherFilter.value = filter
    }

    fun clearAppAdvancedFilters() {
        appInternetPermissionFilter.value = InternetPermissionFilter.All
        appTypeFilter.value = AppTypeFilter.User
        appLauncherFilter.value = LauncherFilter.All
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            appScanning.value = true
            val count = runCatching {
                installedAppRepository.refreshInstalledApps()
            }.getOrElse {
                appLastScanSummary.value = "扫描失败：${it.message ?: "未知错误"}"
                appScanning.value = false
                return@launch
            }
            appLastScanSummary.value = "上次扫描发现 $count 个用户应用"
            appScanning.value = false
        }
    }

    fun setAppManualOnline(packageName: String) {
        viewModelScope.launch {
            installedAppRepository.setManualOnline(packageName)
        }
    }

    fun setAppManualOffline(packageName: String) {
        viewModelScope.launch {
            installedAppRepository.setManualOffline(packageName)
        }
    }

    fun resetAppToDefault(packageName: String) {
        viewModelScope.launch {
            installedAppRepository.resetToDefault(packageName)
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

private data class UiStateBase(
    val settings: UserSettings,
    val runtimeSnapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot,
    val appCount: Int,
    val logCount: Int,
    val accessGateState: AccessGateState,
)

@Immutable
data class ExecutionLogItem(
    val timestampMillis: Long,
    val action: String,
    val executor: String,
    val result: String,
    val detail: String,
)

private fun ExecutionLogEntity.toUiItem(): ExecutionLogItem {
    val actionLabel = when {
        errorMessage?.startsWith("飞行模式状态探测：") == true ||
            errorMessage?.startsWith("移动数据状态探测：") == true -> "状态探测"
        actionType == ExecutionAction.ReconnectNow.name -> "立即恢复联网"
        actionType == ExecutionAction.DisconnectNow.name -> "立即断网"
        actionType == ExecutionAction.DoNothing.name -> "未执行动作"
        else -> runCatching { enumValueOf<ExecutionAction>(actionType).label }.getOrDefault(actionType)
    }
    val executorLabel = runCatching { enumValueOf<ExecutorType>(executorType).label }.getOrDefault(executorType)
    val resultLabel = when (result) {
        ExecutionResult.Success.name -> "成功"
        ExecutionResult.Failed.name -> "失败"
        ExecutionResult.Pending.name -> "待定"
        ExecutionResult.PartialSuccess.name -> "部分成功"
        ExecutionResult.Skipped.name -> "已跳过"
        else -> runCatching { enumValueOf<ExecutionResult>(result).label }.getOrDefault(result)
    }
    return ExecutionLogItem(
        timestampMillis = timestampMillis,
        action = actionLabel,
        executor = executorLabel,
        result = resultLabel,
        detail = errorMessage ?: "无附加信息",
    )
}

private fun buildWifiStatus(snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot): String = when {
    snapshot.isWifiConnected -> "已连接"
    snapshot.isWifiEnabled -> "已开启，未连接"
    else -> "已关闭"
}

private fun buildBluetoothStatus(snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot): String =
    if (!snapshot.isBluetoothStateReadable) {
        "未授权，不可读"
    } else if (snapshot.isBluetoothEnabled) {
        "已开启"
    } else {
        "已关闭"
    }

private fun buildMobileDataStatus(snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot): String =
    when (snapshot.isMobileDataEnabled) {
        true -> "已开启"
        false -> "已关闭"
        null -> "未知"
    }
