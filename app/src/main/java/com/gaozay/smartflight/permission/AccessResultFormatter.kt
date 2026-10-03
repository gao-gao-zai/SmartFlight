package com.gaozay.smartflight.permission

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import javax.inject.Inject

class AccessResultFormatter @Inject constructor() {
    fun buildProbeDetailReason(result: ExecutorCommandResult): String = buildString {
        val mode = result.controlMode ?: NetworkControlMode.AirplaneMode
        append(if (mode == NetworkControlMode.AirplaneMode) AppStrings.get(R.string.probe_airplane_mode_prefix) else AppStrings.get(R.string.probe_mobile_data_prefix))
        append(
            when (result.controlledEnabled) {
                true -> if (mode == NetworkControlMode.AirplaneMode) AppStrings.get(R.string.enabled) else AppStrings.get(R.string.enabled)
                false -> if (mode == NetworkControlMode.AirplaneMode) AppStrings.get(R.string.disabled) else AppStrings.get(R.string.disabled)
                null -> result.summary
            },
        )
        append(AppStrings.get(R.string.executor))
        append(result.executorType.label)
        appendCommandOutput(result)
    }

    fun buildActionDetailReason(reasonPrefix: String, result: ExecutorCommandResult): String = buildString {
        append(reasonPrefix)
        append(AppStrings.get(R.string.detail_separator))
        append(result.summary)
        append(AppStrings.get(R.string.executor))
        append(result.executorType.label)
        appendCommandOutput(result)
    }

    fun buildAirplaneModeDetailReason(
        reasonPrefix: String,
        airplaneResult: ExecutorCommandResult,
        restoreMobileDataResult: ExecutorCommandResult?,
    ): String = buildString {
        append(buildActionDetailReason(reasonPrefix, airplaneResult))
        if (restoreMobileDataResult != null) {
            append(AppStrings.get(R.string.mobile_data_restoration))
            append(restoreMobileDataResult.summary)
            appendCommandOutput(restoreMobileDataResult)
        }
    }

    fun buildPassiveAccessSummary(advancedAccess: AdvancedAccessState): String {
        if (advancedAccess.selectedExecutorType != ExecutorType.Unavailable) {
            return AppStrings.get(R.string.available_execution_channel, advancedAccess.selectedExecutorType.label)
        }
        return advancedAccess.gatingIssues.firstOrNull()?.summary ?: AppStrings.get(R.string.no_executor_available_yet)
    }

    fun shouldRefreshRuntimeStatusSummary(snapshot: RuntimeSnapshot): Boolean {
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

    fun actionFor(
        mode: NetworkControlMode,
        controlledEnabled: Boolean?,
    ): ExecutionAction = when (mode) {
        NetworkControlMode.AirplaneMode -> when (controlledEnabled) {
            true -> ExecutionAction.DisconnectNow
            false -> ExecutionAction.ReconnectNow
            null -> ExecutionAction.DoNothing
        }
        NetworkControlMode.MobileData -> when (controlledEnabled) {
            false -> ExecutionAction.DisconnectNow
            true -> ExecutionAction.ReconnectNow
            null -> ExecutionAction.DoNothing
        }
    }

    fun executionResultFor(result: ExecutorCommandResult): ExecutionResult = when {
        result.alreadyInRequestedState -> ExecutionResult.Skipped
        result.executed && result.exitCode == 0 && result.controlledEnabled != null -> ExecutionResult.Success
        result.executed && result.exitCode == 0 -> ExecutionResult.PartialSuccess
        else -> ExecutionResult.Failed
    }

    fun mergeAirplaneModeExecutionResult(
        disconnected: Boolean,
        airplaneResult: ExecutorCommandResult,
        restoreMobileDataResult: ExecutorCommandResult?,
    ): ExecutionResult {
        val airplaneExecution = executionResultFor(airplaneResult)
        if (airplaneExecution == ExecutionResult.Failed) {
            return ExecutionResult.Failed
        }
        if (disconnected || restoreMobileDataResult == null) {
            return airplaneExecution
        }
        val restoreExecution = executionResultFor(restoreMobileDataResult)
        return when {
            restoreExecution == ExecutionResult.Failed -> ExecutionResult.PartialSuccess
            airplaneExecution == ExecutionResult.Skipped && restoreExecution == ExecutionResult.Skipped -> ExecutionResult.Skipped
            else -> ExecutionResult.Success
        }
    }

    private fun StringBuilder.appendCommandOutput(result: ExecutorCommandResult) {
        if (result.stdout.isNotBlank() && result.stdout.trim() !in setOf("0", "1")) {
            append(AppStrings.get(R.string.output))
            append(result.stdout.trim())
        }
        if (result.stderr.isNotBlank()) {
            append(AppStrings.get(R.string.error))
            append(result.stderr.trim())
        }
    }
}
