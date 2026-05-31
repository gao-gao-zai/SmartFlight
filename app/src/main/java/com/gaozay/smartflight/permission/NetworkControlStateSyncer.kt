package com.gaozay.smartflight.permission

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import javax.inject.Inject

class NetworkControlStateSyncer @Inject constructor(
    private val networkControlProbe: NetworkControlProbe,
    private val formatter: AccessResultFormatter,
    private val snapshotUpdater: AccessRuntimeSnapshotUpdater,
    private val logWriter: AccessExecutionLogWriter,
) {
    suspend fun syncCurrentNetworkControlState() {
        val result = networkControlProbe.probeCurrentNetworkControlState()
        Log.d(
            LOG_TAG,
            "syncCurrentNetworkControlState mode=${result.controlMode} enabled=${result.controlledEnabled} executed=${result.executed} exit=${result.exitCode} executor=${result.executorType} summary=${result.summary}",
        )
        applyProbeResult(
            result = result,
            triggerSource = TriggerSource.ServiceRestored,
            shouldLog = false,
        )
    }

    suspend fun probeCurrentNetworkControlState() {
        val result = networkControlProbe.probeCurrentNetworkControlState()
        Log.d(
            LOG_TAG,
            "probeCurrentNetworkControlState mode=${result.controlMode} enabled=${result.controlledEnabled} executed=${result.executed} exit=${result.exitCode} executor=${result.executorType} summary=${result.summary}",
        )
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
        val detailReason = formatter.buildProbeDetailReason(result)
        snapshotUpdater.applyProbeResult(
            result = result,
            triggerSource = triggerSource,
            executionResult = executionResult,
            detailReason = detailReason,
        )
        Log.d(
            LOG_TAG,
            "applyProbeResult trigger=$triggerSource mode=${result.controlMode} enabled=$controlledEnabled result=$executionResult executor=${result.executorType} shouldLog=$shouldLog reason=$detailReason",
        )
        if (shouldLog) {
            logWriter.addExecutionLog(
                action = ExecutionAction.DoNothing,
                result = executionResult,
                reason = detailReason,
                probeResult = result,
                triggerSource = triggerSource,
            )
        }
    }

    private companion object {
        const val LOG_TAG = "SmartFlightAccess"
    }
}
