package com.gaozay.smartflight.permission

import android.os.Build
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorProbeService
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.executor.ExecutorWriteCommands
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.runtime.withDerivedUnifiedNetworkState
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advancedAccessChecker: AdvancedAccessChecker,
    private val systemPermissionChecker: SystemPermissionChecker,
    private val adbBootstrapRepository: AdbBootstrapRepository,
    private val rootAccessChecker: RootAccessChecker,
    private val executorProbeService: ExecutorProbeService,
    private val executionLogRepository: ExecutionLogRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
) : AccessRepository {
    private val mutableAccessGateState = MutableStateFlow(AccessGateState())

    override val accessGateState: StateFlow<AccessGateState> = mutableAccessGateState.asStateFlow()

    override suspend fun refresh() {
        val advancedAccess = advancedAccessChecker.check()
        val state = AccessGateState(
            advancedAccess = advancedAccess,
            usageStatsAccess = systemPermissionChecker.checkUsageStatsAccess(),
            notificationAccess = systemPermissionChecker.checkNotificationPermission(),
            batteryOptimization = systemPermissionChecker.checkBatteryOptimization(),
            lastCheckedAtMillis = System.currentTimeMillis(),
        )
        mutableAccessGateState.value = state

        runtimeStatusRepository.updateSnapshot { snapshot ->
            val shouldResetRuntimeStatus = shouldRefreshRuntimeStatusSummary(snapshot)
            val passiveSummary = buildPassiveAccessSummary(advancedAccess)
            snapshot.copy(
                activeExecutorType = advancedAccess.selectedExecutorType,
                lastAction = if (shouldResetRuntimeStatus) ExecutionAction.DoNothing else snapshot.lastAction,
                lastTriggerSource = TriggerSource.ServiceRestored,
                lastActionResult = if (shouldResetRuntimeStatus) {
                    if (advancedAccess.isAvailable) ExecutionResult.Success else ExecutionResult.Pending
                } else {
                    snapshot.lastActionResult
                },
                lastActionReason = if (shouldResetRuntimeStatus) {
                    passiveSummary
                } else {
                    snapshot.lastActionReason
                },
                runtimeStatusResult = if (advancedAccess.isAvailable) ExecutionResult.Success else ExecutionResult.Pending,
                runtimeStatusSummary = passiveSummary,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setAdbBootstrapped(bootstrapped: Boolean) {
        adbBootstrapRepository.setBootstrapped(bootstrapped)
        refresh()
    }

    override suspend fun probeRootAccess() {
        rootAccessChecker.probeAuthorization()
        refresh()
    }

    override suspend fun autoGrantCompanionPermissions() {
        val currentState = accessGateState.value
        if (!currentState.advancedAccess.isAvailable) {
            return
        }

        val packageName = context.packageName
        if (!currentState.usageStatsAccess.satisfiesRequirement) {
            executorProbeService.runCommand(
                ExecutorWriteCommands.grantUsageStatsAccess(packageName),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !currentState.notificationAccess.satisfiesRequirement
        ) {
            executorProbeService.runCommand(
                ExecutorWriteCommands.grantNotificationPermission(packageName),
            )
        }
        if (!currentState.batteryOptimization.satisfiesRequirement) {
            executorProbeService.runCommand(
                ExecutorWriteCommands.whitelistBatteryOptimization(packageName),
            )
        }
        refresh()
    }

    override suspend fun syncCurrentNetworkControlState() {
        val result = executorProbeService.probeCurrentNetworkControlState()
        applyProbeResult(
            result = result,
            triggerSource = TriggerSource.ServiceRestored,
            shouldLog = false,
        )
    }

    override suspend fun probeCurrentNetworkControlState() {
        val result = executorProbeService.probeCurrentNetworkControlState()
        applyProbeResult(
            result = result,
            triggerSource = TriggerSource.Manual,
            shouldLog = true,
        )
    }

    private suspend fun applyProbeResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        shouldLog: Boolean,
    ) {
        val controlledEnabled = result.controlledEnabled
        val executionResult = if (result.executed && result.exitCode == 0 && controlledEnabled != null) {
            ExecutionResult.Success
        } else {
            ExecutionResult.Failed
        }
        val detailReason = buildProbeDetailReason(result)
        runtimeStatusRepository.updateSnapshot { snapshot ->
            updateControlStateSnapshot(snapshot, result).copy(
                activeExecutorType = result.executorType,
                lastAction = ExecutionAction.DoNothing,
                lastTriggerSource = triggerSource,
                lastActionResult = executionResult,
                lastActionReason = if (executionResult == ExecutionResult.Failed) {
                    detailReason
                } else {
                    ""
                },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        if (shouldLog) {
            addExecutionLog(
                action = ExecutionAction.DoNothing,
                result = executionResult,
                reason = detailReason,
                probeResult = result,
                triggerSource = triggerSource,
            )
        }
    }

    override suspend fun toggleCurrentNetworkControlState() {
        val result = executorProbeService.toggleCurrentNetworkControlState()
        applyNetworkControlResult(
            result = result,
            triggerSource = TriggerSource.Manual,
            reasonPrefix = when (result.controlMode ?: NetworkControlMode.AirplaneMode) {
                NetworkControlMode.AirplaneMode -> "手动切换飞行模式"
                NetworkControlMode.MobileData -> "手动切换移动数据"
            },
        )
    }

    override suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) {
        val result = executorProbeService.setDisconnectedState(disconnected)
        val mode = result.controlMode ?: NetworkControlMode.AirplaneMode
        applyNetworkControlResult(
            result = result,
            triggerSource = triggerSource,
            reasonPrefix = reason ?: when (mode) {
                NetworkControlMode.AirplaneMode ->
                    if (disconnected) "自动开启飞行模式" else "自动关闭飞行模式"
                NetworkControlMode.MobileData ->
                    if (disconnected) "自动关闭移动数据" else "自动开启移动数据"
            },
        )
    }

    private suspend fun addExecutionLog(
        action: ExecutionAction,
        result: ExecutionResult,
        reason: String,
        probeResult: ExecutorCommandResult,
        triggerSource: TriggerSource,
    ) {
        val snapshot = runtimeStatusRepository.snapshot.first()
        executionLogRepository.addLog(
            ExecutionLogEntity(
                timestampMillis = System.currentTimeMillis(),
                triggerSource = triggerSource.name,
                foregroundPackageName = snapshot.currentForegroundPackageName,
                foregroundAppLabel = snapshot.currentForegroundAppLabel,
                screenState = snapshot.screenState.name.ifBlank { ScreenState.Unknown.name },
                isWifiConnected = snapshot.isWifiConnected,
                isWifiEnabled = snapshot.isWifiEnabled,
                isBluetoothEnabled = snapshot.isBluetoothEnabled,
                matchedRules = "",
                actionType = action.name,
                executorType = probeResult.executorType.name,
                result = result.name,
                errorMessage = reason,
            ),
        )
    }

    private suspend fun applyNetworkControlResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        reasonPrefix: String,
    ) {
        val mode = result.controlMode ?: NetworkControlMode.AirplaneMode
        val action = when (mode) {
            NetworkControlMode.AirplaneMode -> when (result.controlledEnabled) {
                true -> ExecutionAction.DisconnectNow
                false -> ExecutionAction.ReconnectNow
                null -> ExecutionAction.DoNothing
            }
            NetworkControlMode.MobileData -> when (result.controlledEnabled) {
                false -> ExecutionAction.DisconnectNow
                true -> ExecutionAction.ReconnectNow
                null -> ExecutionAction.DoNothing
            }
        }
        val executionResult = when {
            result.summary.contains("已处于") -> ExecutionResult.Skipped
            result.executed && result.exitCode == 0 && result.controlledEnabled != null -> ExecutionResult.Success
            result.executed && result.exitCode == 0 -> ExecutionResult.PartialSuccess
            else -> ExecutionResult.Failed
        }
        val detailReason = buildActionDetailReason(reasonPrefix, result)
        runtimeStatusRepository.updateSnapshot { snapshot ->
            updateControlStateSnapshot(snapshot, result).copy(
                activeExecutorType = result.executorType,
                lastAction = action,
                lastTriggerSource = triggerSource,
                lastActionResult = executionResult,
                lastActionReason = if (executionResult == ExecutionResult.Success || executionResult == ExecutionResult.Skipped) {
                    ""
                } else {
                    detailReason
                },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        addExecutionLog(
            action = action,
            result = executionResult,
            reason = detailReason,
            probeResult = result,
            triggerSource = triggerSource,
        )
    }

    private fun buildProbeDetailReason(result: ExecutorCommandResult): String = buildString {
        val mode = result.controlMode ?: NetworkControlMode.AirplaneMode
        append(if (mode == NetworkControlMode.AirplaneMode) "飞行模式状态探测：" else "移动数据状态探测：")
        append(when (result.controlledEnabled) {
            true -> if (mode == NetworkControlMode.AirplaneMode) "已开启" else "已开启"
            false -> if (mode == NetworkControlMode.AirplaneMode) "已关闭" else "已关闭"
            null -> result.summary
        })
        append(" · 执行器：")
        append(result.executorType.label)
        if (result.stdout.isNotBlank() && result.stdout.trim() !in setOf("0", "1")) {
            append(" · 输出：")
            append(result.stdout.trim())
        }
        if (result.stderr.isNotBlank()) {
            append(" · 错误：")
            append(result.stderr.trim())
        }
    }

    private fun buildActionDetailReason(reasonPrefix: String, result: ExecutorCommandResult): String = buildString {
        append(reasonPrefix)
        append("：")
        append(result.summary)
        append(" · 执行器：")
        append(result.executorType.label)
        if (result.stdout.isNotBlank() && result.stdout.trim() !in setOf("0", "1")) {
            append(" · 输出：")
            append(result.stdout.trim())
        }
        if (result.stderr.isNotBlank()) {
            append(" · 错误：")
            append(result.stderr.trim())
        }
    }

    private fun updateControlStateSnapshot(
        snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot,
        result: ExecutorCommandResult,
    ): com.gaozay.smartflight.runtime.RuntimeSnapshot {
        val controlledEnabled = result.controlledEnabled
        return when (result.controlMode ?: NetworkControlMode.AirplaneMode) {
            NetworkControlMode.AirplaneMode -> snapshot.copy(
                isAirplaneModeEnabled = controlledEnabled ?: snapshot.isAirplaneModeEnabled,
            ).withDerivedUnifiedNetworkState()
            NetworkControlMode.MobileData -> snapshot.copy(
                isMobileDataEnabled = controlledEnabled ?: snapshot.isMobileDataEnabled,
            ).withDerivedUnifiedNetworkState()
        }
    }

    private fun buildPassiveAccessSummary(advancedAccess: AdvancedAccessState): String {
        if (advancedAccess.selectedExecutorType != com.gaozay.smartflight.domain.model.ExecutorType.Unavailable) {
            return "当前可用通道：${advancedAccess.selectedExecutorType.label}"
        }
        return advancedAccess.gatingIssues.firstOrNull()?.summary ?: "尚无可用执行器"
    }

    private fun shouldRefreshRuntimeStatusSummary(snapshot: com.gaozay.smartflight.runtime.RuntimeSnapshot): Boolean {
        if (snapshot.lastAction == ExecutionAction.DoNothing &&
            snapshot.lastActionReason == "Runtime not started yet"
        ) {
            return true
        }
        if (snapshot.lastAction == ExecutionAction.PauseAutomation) {
            return true
        }
        return snapshot.lastTriggerSource == TriggerSource.ServiceRestored &&
            snapshot.lastAction == ExecutionAction.DoNothing
    }
}
