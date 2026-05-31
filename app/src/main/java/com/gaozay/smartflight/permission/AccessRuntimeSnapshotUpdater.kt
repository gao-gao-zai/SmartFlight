package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.runtime.withDerivedUnifiedNetworkState
import javax.inject.Inject

class AccessRuntimeSnapshotUpdater @Inject constructor(
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val formatter: AccessResultFormatter,
) {
    suspend fun updatePassiveAccessSummary(advancedAccess: AdvancedAccessState) {
        runtimeStatusRepository.updateSnapshot { snapshot ->
            val shouldResetRuntimeStatus = formatter.shouldRefreshRuntimeStatusSummary(snapshot)
            val passiveSummary = formatter.buildPassiveAccessSummary(advancedAccess)
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

    suspend fun applyProbeResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        executionResult: ExecutionResult,
        detailReason: String,
    ) {
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
    }

    suspend fun applyNetworkControlResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        action: ExecutionAction,
        executionResult: ExecutionResult,
        detailReason: String,
    ) {
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
    }

    suspend fun applyAirplaneModeResult(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        action: ExecutionAction,
        finalResult: ExecutionResult,
        detailReason: String,
        airplaneResult: ExecutorCommandResult,
        restoreMobileDataResult: ExecutorCommandResult?,
        mobileDataStateToRemember: Boolean?,
        airplaneExecutionResult: ExecutionResult,
    ) {
        runtimeStatusRepository.updateSnapshot { current ->
            var updated = updateControlStateSnapshot(current, airplaneResult)
            if (disconnected && airplaneExecutionResult != ExecutionResult.Failed) {
                updated = updated.copy(
                    rememberedMobileDataEnabledBeforeAirplaneMode = mobileDataStateToRemember,
                )
            }
            if (!disconnected) {
                updated = when {
                    restoreMobileDataResult?.controlledEnabled != null -> updated.copy(
                        isMobileDataEnabled = restoreMobileDataResult.controlledEnabled,
                    )
                    finalResult != ExecutionResult.Failed && mobileDataStateToRemember != null -> updated.copy(
                        isMobileDataEnabled = mobileDataStateToRemember,
                    )
                    else -> updated
                }
                if (restoreMobileDataResult == null || formatter.executionResultFor(restoreMobileDataResult) != ExecutionResult.Failed) {
                    updated = updated.copy(
                        rememberedMobileDataEnabledBeforeAirplaneMode = null,
                    )
                }
            }
            updated.copy(
                activeExecutorType = airplaneResult.executorType,
                lastAction = action,
                lastTriggerSource = triggerSource,
                lastActionResult = finalResult,
                lastActionReason = if (finalResult == ExecutionResult.Success || finalResult == ExecutionResult.Skipped) {
                    ""
                } else {
                    detailReason
                },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    fun updateControlStateSnapshot(
        snapshot: RuntimeSnapshot,
        result: ExecutorCommandResult,
    ): RuntimeSnapshot {
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
}
