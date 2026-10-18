package com.gaozay.smartflight.permission

import android.util.Log
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.runtime.snapshotState
import com.gaozay.smartflight.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

@Singleton
class NetworkControlActionExecutor @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val networkControlProbe: NetworkControlProbe,
    private val formatter: AccessResultFormatter,
    private val snapshotUpdater: AccessRuntimeSnapshotUpdater,
    private val logWriter: AccessExecutionLogWriter,
) {
    private val actionMutex = Mutex()

    suspend fun toggleCurrentNetworkControlState() = actionMutex.withLock {
        toggleCurrentNetworkControlStateLocked()
    }

    private suspend fun toggleCurrentNetworkControlStateLocked() {
        val mode = settingsRepository.settings.first().networkControlMode
        val reason = if (mode == NetworkControlMode.AirplaneMode) {
            AppStrings.get(R.string.manually_toggle_airplane_mode)
        } else {
            AppStrings.get(R.string.manually_toggle_mobile_data)
        }
        val observed = networkControlProbe.probeNetworkControlState(mode)
        val currentEnabled = observed.controlledEnabled
        if (currentEnabled == null) {
            applyNetworkControlResult(observed, TriggerSource.Manual, reason)
            return
        }
        setDisconnectedStateLocked(
            mode = mode,
            disconnected = if (mode == NetworkControlMode.AirplaneMode) !currentEnabled else currentEnabled,
            triggerSource = TriggerSource.Manual,
            reason = reason,
        )
    }
    suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) = actionMutex.withLock {
        val mode = settingsRepository.settings.first().networkControlMode
        setDisconnectedStateLocked(mode, disconnected, triggerSource, reason)
    }

    private suspend fun setDisconnectedStateLocked(
        mode: NetworkControlMode,
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) {
        if (mode == NetworkControlMode.AirplaneMode) {
            applyAirplaneModeResult(
                disconnected = disconnected,
                triggerSource = triggerSource,
                reasonPrefix = reason ?: if (disconnected) AppStrings.get(R.string.automatically_enable_airplane_mode) else AppStrings.get(R.string.automatically_disable_airplane_mode),
            )
            return
        }
        val result = networkControlProbe.setNetworkControlEnabled(
            mode = mode,
            enabled = !disconnected,
        )
        applyNetworkControlResult(
            result = result,
            triggerSource = triggerSource,
            reasonPrefix = reason ?: when (mode) {
                NetworkControlMode.AirplaneMode ->
                    if (disconnected) AppStrings.get(R.string.automatically_enable_airplane_mode) else AppStrings.get(R.string.automatically_disable_airplane_mode)
                NetworkControlMode.MobileData ->
                    if (disconnected) AppStrings.get(R.string.automatically_disable_mobile_data) else AppStrings.get(R.string.automatically_enable_mobile_data)
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
            snapshot.rememberedMobileDataEnabledBeforeAirplaneMode
                ?: networkControlProbe.probeNetworkControlState(NetworkControlMode.MobileData).controlledEnabled
        } else {
            snapshot.rememberedMobileDataEnabledBeforeAirplaneMode
        }
        val airplaneResult = networkControlProbe.setNetworkControlEnabled(
            mode = NetworkControlMode.AirplaneMode,
            enabled = disconnected,
        )
        val airplaneExecutionResult = formatter.executionResultFor(airplaneResult)
        val restoreMobileDataResult = if (!disconnected &&
            airplaneResult.controlledEnabled == false &&
            mobileDataStateToRemember != null
        ) {
            networkControlProbe.setNetworkControlEnabled(
                mode = NetworkControlMode.MobileData,
                enabled = mobileDataStateToRemember,
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
