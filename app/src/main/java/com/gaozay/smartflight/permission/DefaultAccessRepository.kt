package com.gaozay.smartflight.permission

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorProbeService
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.executor.ExecutorValidationService
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAccessRepository @Inject constructor(
    private val advancedAccessChecker: AdvancedAccessChecker,
    private val systemPermissionChecker: SystemPermissionChecker,
    private val adbBootstrapRepository: AdbBootstrapRepository,
    private val rootAccessChecker: RootAccessChecker,
    private val executorProbeService: ExecutorProbeService,
    private val executorValidationService: ExecutorValidationService,
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

        val allExecutors = executorValidationService.validateAll()
        val bestExecutor = executorValidationService.selectBestExecutor(allExecutors)
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                activeExecutorType = advancedAccess.selectedExecutorType,
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = if (bestExecutor.isReady) ExecutionResult.Success else ExecutionResult.Pending,
                lastActionReason = buildString {
                    append("执行器自检：")
                    append(bestExecutor.summary)
                    bestExecutor.detail?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" | 全部结果：")
                    append(allExecutors.joinToString(separator = "；") { result ->
                        "${result.executorType.label}:${result.summary}"
                    })
                },
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

    override suspend fun probeAirplaneModeState() {
        val result = executorProbeService.probeAirplaneModeState()
        val lastActionReason = buildString {
            append("飞行模式状态探测：")
            append(
                when (result.stdout.trim()) {
                    "1" -> "已开启"
                    "0" -> "已关闭"
                    else -> result.summary
                },
            )
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
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                activeExecutorType = result.executorType,
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = if (result.executed && result.exitCode == 0) {
                    ExecutionResult.Success
                } else {
                    ExecutionResult.Failed
                },
                lastActionReason = lastActionReason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        addExecutionLog(
            action = ExecutionAction.DoNothing,
            result = if (result.executed && result.exitCode == 0) ExecutionResult.Success else ExecutionResult.Failed,
            reason = lastActionReason,
            probeResult = result,
            triggerSource = TriggerSource.Manual,
        )
    }

    override suspend fun toggleAirplaneModeState() {
        val result = executorProbeService.toggleAirplaneModeState()
        applyAirplaneModeResult(
            result = result,
            triggerSource = TriggerSource.Manual,
            reasonPrefix = "手动切换飞行模式",
        )
    }

    override suspend fun setAirplaneModeState(
        enabled: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) {
        val result = executorProbeService.setAirplaneModeState(enabled)
        applyAirplaneModeResult(
            result = result,
            triggerSource = triggerSource,
            reasonPrefix = reason ?: if (enabled) "自动开启飞行模式" else "自动关闭飞行模式",
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

    private suspend fun applyAirplaneModeResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        reasonPrefix: String,
    ) {
        val action = when (result.summary) {
            "飞行模式已开启", "飞行模式已处于开启状态" -> ExecutionAction.DisconnectNow
            "飞行模式已关闭", "飞行模式已处于关闭状态" -> ExecutionAction.ReconnectNow
            else -> ExecutionAction.DoNothing
        }
        val executionResult = when {
            result.summary.contains("已处于") -> ExecutionResult.Skipped
            result.executed && result.exitCode == 0 -> ExecutionResult.Success
            else -> ExecutionResult.Failed
        }
        val lastActionReason = buildString {
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
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                activeExecutorType = result.executorType,
                lastAction = action,
                lastActionResult = executionResult,
                lastActionReason = lastActionReason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        addExecutionLog(
            action = action,
            result = executionResult,
            reason = lastActionReason,
            probeResult = result,
            triggerSource = triggerSource,
        )
    }
}
