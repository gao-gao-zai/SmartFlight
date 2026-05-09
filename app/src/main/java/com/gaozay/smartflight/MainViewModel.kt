package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.status
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.AppListStatus
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.executor.ExecutorValidationService
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.AutomationServiceController
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.SettingsRepository
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
    private val executorValidationService: ExecutorValidationService,
    private val automationServiceController: AutomationServiceController,
) : ViewModel() {
    private val appQuery = MutableStateFlow("")
    private val appFilter = MutableStateFlow(AppFilter.All)
    private val appScanning = MutableStateFlow(false)
    private val appLastScanSummary = MutableStateFlow("尚未扫描")
    private val diagnosticsState = MutableStateFlow<List<ExecutorDiagnosticItem>>(emptyList())
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
            advancedAccess = base.accessGateState.advancedAccess.selectedExecutorType.label,
            currentMode = base.settings.networkControlMode.label,
            automationEnabled = base.settings.automationEnabled,
            monitorForegroundWhenScreenOff = base.settings.monitorForegroundWhenScreenOff,
            foregroundApp = base.runtimeSnapshot.currentForegroundAppLabel
                ?: base.runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            runtimeExecutor = base.runtimeSnapshot.activeExecutorType.label,
            runtimeLastCheck = buildRuntimeSummary(base.settings, base.runtimeSnapshot),
            runtimeLastResult = base.runtimeSnapshot.lastActionResult.label,
            runtimeUpdatedAtMillis = base.runtimeSnapshot.updatedAtMillis,
            executorDiagnostics = diagnosticsState.value,
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

    val appsUiState: StateFlow<AppsUiState> = combine(
        installedAppRepository.observeApps(),
        appQuery.asStateFlow(),
        appFilter.asStateFlow(),
        appScanning.asStateFlow(),
        appLastScanSummary.asStateFlow(),
    ) { apps, query, filter, isScanning, lastScanSummary ->
        val filteredApps = apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppFilter.All -> true
                AppFilter.Candidate -> app.status() == AppListStatus.Candidate
                AppFilter.Whitelist -> app.status() == AppListStatus.Whitelist
                AppFilter.Blacklist -> app.status() == AppListStatus.Blacklist
                AppFilter.Ignored -> app.status() == AppListStatus.Ignored
            }
            matchesQuery && matchesFilter
        }
        AppsUiState(
            apps = filteredApps,
            query = query,
            filter = filter,
            totalCount = apps.size,
            candidateCount = apps.count { it.status() == AppListStatus.Candidate },
            whitelistCount = apps.count { it.status() == AppListStatus.Whitelist },
            blacklistCount = apps.count { it.status() == AppListStatus.Blacklist },
            ignoredCount = apps.count { it.status() == AppListStatus.Ignored },
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
            diagnosticsState.value = executorValidationService.validateAll().map { result ->
                ExecutorDiagnosticItem(
                    executor = result.executorType.label,
                    summary = result.summary,
                    detail = result.detail.orEmpty(),
                    command = result.command.orEmpty(),
                    output = result.commandOutput.orEmpty(),
                    ready = result.isReady,
                )
            }
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
            diagnosticsState.value = executorValidationService.validateAll().map { result ->
                ExecutorDiagnosticItem(
                    executor = result.executorType.label,
                    summary = result.summary,
                    detail = result.detail.orEmpty(),
                    command = result.command.orEmpty(),
                    output = result.commandOutput.orEmpty(),
                    ready = result.isReady,
                )
            }
        }
    }

    fun toggleAirplaneModeState() {
        viewModelScope.launch {
            accessRepository.toggleAirplaneModeState()
            diagnosticsState.value = executorValidationService.validateAll().map { result ->
                ExecutorDiagnosticItem(
                    executor = result.executorType.label,
                    summary = result.summary,
                    detail = result.detail.orEmpty(),
                    command = result.command.orEmpty(),
                    output = result.commandOutput.orEmpty(),
                    ready = result.isReady,
                )
            }
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
            appLastScanSummary.value = "上次扫描发现 $count 个已安装应用"
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

private data class UiStateBase(
    val settings: com.gaozay.smartflight.settings.UserSettings,
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
    settings: com.gaozay.smartflight.settings.UserSettings,
    snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot,
): String {
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

                else -> "已取消待执行的息屏延迟断网"
            }

            ExecutionResult.Skipped -> when (snapshot.lastTriggerSource) {
                com.gaozay.smartflight.domain.model.TriggerSource.UserUnlocked ->
                    "用户已解锁，当前没有待取消的息屏延迟断网"

                com.gaozay.smartflight.domain.model.TriggerSource.ScreenOn ->
                    "屏幕已点亮，当前没有待取消的息屏延迟断网"

                else -> "当前没有待取消的息屏延迟断网"
            }

            else -> snapshot.lastActionReason
        }
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
