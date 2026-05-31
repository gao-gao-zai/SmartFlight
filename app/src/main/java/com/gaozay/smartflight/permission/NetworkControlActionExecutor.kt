package com.gaozay.smartflight.permission

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.runtime.isDisconnected
import com.gaozay.smartflight.runtime.snapshotState
import com.gaozay.smartflight.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class NetworkControlActionExecutor @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val networkControlProbe: NetworkControlProbe,
    private val formatter: AccessResultFormatter,
    private val snapshotUpdater: AccessRuntimeSnapshotUpdater,
    private val logWriter: AccessExecutionLogWriter,
) {
    suspend fun toggleCurrentNetworkControlState() {
        val snapshot = runtimeStatusRepository.snapshotState()
        val mode = settingsRepository.settings.first().networkControlMode
        if (mode == NetworkControlMode.AirplaneMode) {
            val currentEnabled = snapshot.isAirplaneModeEnabled
                ?: networkControlProbe.probeNetworkControlState(NetworkControlMode.AirplaneMode).controlledEnabled
            if (currentEnabled == null) {
                applyNetworkControlResult(
                    result = networkControlProbe.probeNetworkControlState(NetworkControlMode.AirplaneMode),
                    triggerSource = TriggerSource.Manual,
                    reasonPrefix = "手动切换飞行模式",
                )
                return
            }
            setDisconnectedState(
                disconnected = !currentEnabled,
                triggerSource = TriggerSource.Manual,
                reason = "手动切换飞行模式",
            )
            return
        }
        val knownControlledEnabled = snapshot.isMobileDataEnabled
        val result = networkControlProbe.toggleCurrentNetworkControlState(
            knownControlledEnabled = knownControlledEnabled,
        )
        applyNetworkControlResult(
            result = result,
            triggerSource = TriggerSource.Manual,
            reasonPrefix = when (result.controlMode ?: NetworkControlMode.AirplaneMode) {
                NetworkControlMode.AirplaneMode -> "手动切换飞行模式"
                NetworkControlMode.MobileData -> "手动切换移动数据"
            },
        )
    }

    suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) {
        val mode = settingsRepository.settings.first().networkControlMode
        if (mode == NetworkControlMode.AirplaneMode) {
            applyAirplaneModeResult(
                disconnected = disconnected,
                triggerSource = triggerSource,
                reasonPrefix = reason ?: if (disconnected) "自动开启飞行模式" else "自动关闭飞行模式",
            )
            return
        }
        val knownDisconnected = runtimeStatusRepository.snapshotState().isDisconnected(mode)
        val result = networkControlProbe.setDisconnectedState(
            disconnected = disconnected,
            knownDisconnected = knownDisconnected,
        )
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

    private suspend fun applyAirplaneModeResult(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reasonPrefix: String,
    ) {
        val snapshot = runtimeStatusRepository.snapshotState()
        val mobileDataStateToRemember = if (disconnected) {
            snapshot.isMobileDataEnabled
                ?: networkControlProbe.probeNetworkControlState(NetworkControlMode.MobileData).controlledEnabled
        } else {
            snapshot.rememberedMobileDataEnabledBeforeAirplaneMode
        }
        val airplaneResult = networkControlProbe.setNetworkControlEnabled(
            mode = NetworkControlMode.AirplaneMode,
            enabled = disconnected,
            knownCurrentEnabled = snapshot.isAirplaneModeEnabled,
        )
        val airplaneExecutionResult = formatter.executionResultFor(airplaneResult)
        val restoreMobileDataResult = if (!disconnected &&
            airplaneExecutionResult != ExecutionResult.Failed &&
            mobileDataStateToRemember != null
        ) {
            networkControlProbe.setNetworkControlEnabled(
                mode = NetworkControlMode.MobileData,
                enabled = mobileDataStateToRemember,
                knownCurrentEnabled = snapshot.isMobileDataEnabled,
            )
        } else {
            null
        }
        val action = if (disconnected) ExecutionAction.DisconnectNow else ExecutionAction.ReconnectNow
        val finalResult = formatter.mergeAirplaneModeExecutionResult(
            disconnected = disconnected,
            airplaneResult = airplaneResult,
            restoreMobileDataResult = restoreMobileDataResult,
        )
        val detailReason = formatter.buildAirplaneModeDetailReason(
            reasonPrefix = reasonPrefix,
            airplaneResult = airplaneResult,
            restoreMobileDataResult = restoreMobileDataResult,
        )
        Log.d(
            LOG_TAG,
            "applyAirplaneModeResult disconnected=$disconnected trigger=$triggerSource airplaneEnabled=${airplaneResult.controlledEnabled} airplaneExit=${airplaneResult.exitCode} restoreMobile=${restoreMobileDataResult?.controlledEnabled} finalResult=$finalResult executor=${airplaneResult.executorType} reason=$detailReason",
        )
        snapshotUpdater.applyAirplaneModeResult(
            disconnected = disconnected,
            triggerSource = triggerSource,
            action = action,
            finalResult = finalResult,
            detailReason = detailReason,
            airplaneResult = airplaneResult,
            restoreMobileDataResult = restoreMobileDataResult,
            mobileDataStateToRemember = mobileDataStateToRemember,
            airplaneExecutionResult = airplaneExecutionResult,
        )
        logWriter.addExecutionLog(
            action = action,
            result = finalResult,
            reason = detailReason,
            probeResult = restoreMobileDataResult ?: airplaneResult,
            triggerSource = triggerSource,
        )
    }

    private suspend fun applyNetworkControlResult(
        result: ExecutorCommandResult,
        triggerSource: TriggerSource,
        reasonPrefix: String,
    ) {
        val mode = result.controlMode ?: NetworkControlMode.AirplaneMode
        val action = formatter.actionFor(mode, result.controlledEnabled)
        val executionResult = formatter.executionResultFor(result)
        val detailReason = formatter.buildActionDetailReason(reasonPrefix, result)
        Log.d(
            LOG_TAG,
            "applyNetworkControlResult trigger=$triggerSource mode=$mode enabled=${result.controlledEnabled} exit=${result.exitCode} executed=${result.executed} result=$executionResult executor=${result.executorType} reason=$detailReason",
        )
        snapshotUpdater.applyNetworkControlResult(
            result = result,
            triggerSource = triggerSource,
            action = action,
            executionResult = executionResult,
            detailReason = detailReason,
        )
        logWriter.addExecutionLog(
            action = action,
            result = executionResult,
            reason = detailReason,
            probeResult = result,
            triggerSource = triggerSource,
        )
    }

    private companion object {
        const val LOG_TAG = "SmartFlightAccess"
    }
}
