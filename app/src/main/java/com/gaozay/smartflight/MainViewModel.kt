package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.status
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.AppListStatus
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
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
            automationEnabled = base.settings.automationEnabled,
            monitorForegroundWhenScreenOff = base.settings.monitorForegroundWhenScreenOff,
            foregroundApp = base.runtimeSnapshot.currentForegroundAppLabel
                ?: base.runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            runtimeExecutor = base.runtimeSnapshot.activeExecutorType.label,
            runtimeLastCheck = base.runtimeSnapshot.runtimeStatusSummary,
            runtimeLastResult = base.runtimeSnapshot.runtimeStatusResult.label,
            runtimeUpdatedAtMillis = base.runtimeSnapshot.updatedAtMillis,
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
        val filter = filterState.filter
        val internetPermissionFilter = filterState.internetPermissionFilter
        val typeFilter = filterState.typeFilter
        val launcherFilter = filterState.launcherFilter
        val filteredApps = apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchesStatusFilter = when (filter) {
                AppFilter.All -> true
                AppFilter.Candidate -> app.status() == AppListStatus.Candidate
                AppFilter.Whitelist -> app.status() == AppListStatus.Whitelist
                AppFilter.Blacklist -> app.status() == AppListStatus.Blacklist
                AppFilter.Ignored -> app.status() == AppListStatus.Ignored
            }
            val matchesInternetPermissionFilter = when (internetPermissionFilter) {
                InternetPermissionFilter.All -> true
                InternetPermissionFilter.Declared -> app.declaresInternetPermission
                InternetPermissionFilter.NotDeclared -> !app.declaresInternetPermission
            }
            val matchesTypeFilter = when (typeFilter) {
                AppTypeFilter.All -> true
                AppTypeFilter.User -> !app.isSystemApp
                AppTypeFilter.System -> app.isSystemApp
            }
            val matchesLauncherFilter = when (launcherFilter) {
                LauncherFilter.All -> true
                LauncherFilter.HasLauncher -> app.hasLauncherEntry
                LauncherFilter.NoLauncher -> !app.hasLauncherEntry
            }
            matchesQuery &&
                matchesStatusFilter &&
                matchesInternetPermissionFilter &&
                matchesTypeFilter &&
                matchesLauncherFilter
        }
        AppsUiState(
            apps = filteredApps,
            query = query,
            filter = filter,
            internetPermissionFilter = internetPermissionFilter,
            appTypeFilter = typeFilter,
            launcherFilter = launcherFilter,
            totalCount = apps.size,
            candidateCount = apps.count { it.status() == AppListStatus.Candidate },
            whitelistCount = apps.count { it.status() == AppListStatus.Whitelist },
            blacklistCount = apps.count { it.status() == AppListStatus.Blacklist },
            ignoredCount = apps.count { it.status() == AppListStatus.Ignored },
            filteredCount = filteredApps.size,
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

    fun probeAirplaneModeState() {
        viewModelScope.launch {
            accessRepository.probeAirplaneModeState()
        }
    }

    fun toggleAirplaneModeState() {
        viewModelScope.launch {
            accessRepository.toggleAirplaneModeState()
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutomationEnabled(enabled)
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

    fun setAppListStatus(packageName: String, status: AppListStatus) {
        viewModelScope.launch {
            installedAppRepository.setListStatus(packageName, status)
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
    val monitorForegroundWhenScreenOff: Boolean = false,
    val foregroundApp: String = "尚未连接",
    val runtimeExecutor: String = "不可用",
    val runtimeLastCheck: String = "尚未执行自检",
    val runtimeLastResult: String = ExecutionResult.Pending.label,
    val runtimeUpdatedAtMillis: Long = 0,
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

private data class AppFilterState(
    val filter: AppFilter,
    val internetPermissionFilter: InternetPermissionFilter,
    val typeFilter: AppTypeFilter,
    val launcherFilter: LauncherFilter,
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
        errorMessage?.startsWith("飞行模式状态探测：") == true -> "状态探测"
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

private fun buildRuntimeSummary(
    settings: UserSettings,
    snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot,
): String {
    if (snapshot.isAppExitDisconnectScheduled) {
        val pendingAt = snapshot.pendingAppExitDisconnectAtMillis
        val remainingSeconds = pendingAt?.let {
            ((it - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
        } ?: settings.appExitDelaySeconds.toLong()
        return "白名单应用已离开前台，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.isScreenOffDisconnectScheduled) {
        val pendingAt = snapshot.pendingScreenOffDisconnectAtMillis
        val remainingSeconds = pendingAt?.let {
            ((it - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
        } ?: settings.screenOffDelaySeconds.toLong()
        return "屏幕已熄灭，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.screenState == com.gaozay.smartflight.domain.model.ScreenState.ScreenOff &&
        !settings.monitorForegroundWhenScreenOff
    ) {
        return "屏幕已熄灭，已按设置暂停前台应用监听"
    }
    if (snapshot.lastAction == ExecutionAction.CancelScheduledDisconnect) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (snapshot.lastTriggerSource) {
                com.gaozay.smartflight.domain.model.TriggerSource.UserUnlocked ->
                    "用户已解锁，已取消待执行的息屏延迟断网"

                com.gaozay.smartflight.domain.model.TriggerSource.ScreenOn ->
                    "屏幕已点亮，已取消待执行的息屏延迟断网"

                com.gaozay.smartflight.domain.model.TriggerSource.AppForegroundChanged ->
                    "白名单应用已重新进入前台，已取消待执行的离开应用延迟断网"

                else -> "已取消待执行的息屏延迟断网"
            }

            ExecutionResult.Skipped -> when (snapshot.lastTriggerSource) {
                com.gaozay.smartflight.domain.model.TriggerSource.UserUnlocked ->
                    "用户已解锁，当前没有待取消的息屏延迟断网"

                com.gaozay.smartflight.domain.model.TriggerSource.ScreenOn ->
                    "屏幕已点亮，当前没有待取消的息屏延迟断网"

                com.gaozay.smartflight.domain.model.TriggerSource.AppForegroundChanged ->
                    "当前没有待取消的离开应用延迟断网"

                else -> "当前没有待取消的息屏延迟断网"
            }

            else -> snapshot.lastActionReason
        }
    }
    if (snapshot.lastAction == ExecutionAction.ScheduleAppExitDisconnect) {
        val remainingSeconds = snapshot.pendingAppExitDisconnectAtMillis?.let {
            ((it - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
        } ?: settings.appExitDelaySeconds.toLong()
        return "白名单应用已离开前台，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.lastAction == ExecutionAction.DoNothing &&
        snapshot.lastTriggerSource == com.gaozay.smartflight.domain.model.TriggerSource.Manual
    ) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (snapshot.isAirplaneModeEnabled) {
                true -> "飞行模式当前已开启"
                false -> "飞行模式当前已关闭"
                null -> "飞行模式状态已同步"
            }

            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank { "飞行模式状态探测失败" }
            else -> snapshot.lastActionReason.ifBlank { "飞行模式状态待确认" }
        }
    }
    if (snapshot.lastAction == ExecutionAction.DisconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> "已开启飞行模式，当前处于断网状态"
            ExecutionResult.Skipped -> "飞行模式原本已开启，无需重复断网"
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank { "开启飞行模式失败" }
            else -> snapshot.lastActionReason.ifBlank { "正在执行断网" }
        }
    }
    if (snapshot.lastAction == ExecutionAction.ReconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> "已关闭飞行模式，当前已恢复联网"
            ExecutionResult.Skipped -> "飞行模式原本已关闭，无需重复恢复联网"
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank { "关闭飞行模式失败" }
            else -> snapshot.lastActionReason.ifBlank { "正在执行恢复联网" }
        }
    }
    return snapshot.lastActionReason
}
