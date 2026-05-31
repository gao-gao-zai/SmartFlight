package com.gaozay.smartflight.permission

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AccessExecutionLogWriter @Inject constructor(
    private val executionLogRepository: ExecutionLogRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
) {
    suspend fun addExecutionLog(
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
}
