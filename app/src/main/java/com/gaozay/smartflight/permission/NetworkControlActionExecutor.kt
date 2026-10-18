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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.gaozay.smartflight.runtime.withDerivedUnifiedNetworkState
import kotlinx.coroutines.flow.first

@Singleton
class NetworkControlActionExecutor @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val networkControlProbe: NetworkControlProbe,
    private val formatter: AccessResultFormatter,
    private val snapshotUpdater: AccessRuntimeSnapshotUpdater,
    private val logWriter: AccessExecutionLogWriter,
    private val transaction: NetworkControlTransaction = NetworkControlTransaction(),
) {
    suspend fun toggleCurrentNetworkControlState() = performAction(TriggerSource.Manual) {
        toggleCurrentNetworkControlStateLocked()
    }

    private suspend fun performAction(
        triggerSource: TriggerSource,
        action: ExecutionAction = ExecutionAction.DoNothing,
        block: suspend () -> Unit,
    ) = transaction.run {
        try {
            block()
        } catch (cancelled: CancellationException) {
            // The shell may have applied a write before cancellation or a Binder
            // timeout. Keep restore memory and require a fresh observation.
            withContext(NonCancellable) {
                runtimeStatusRepository.updateSnapshot {
                    it.copy(isAirplaneModeEnabled = null, isMobileDataEnabled = null,
                        lastAction = action,
                        lastTriggerSource = triggerSource,
                        lastActionResult = ExecutionResult.Failed,
                        lastActionReason = AppStrings.get(R.string.network_action_cancelled_state_unknown),
                        updatedAtMillis = System.currentTimeMillis()).withDerivedUnifiedNetworkState()
                }
            }
            throw cancelled
        }
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
    ) = performAction(triggerSource, if (disconnected) ExecutionAction.DisconnectNow else ExecutionAction.ReconnectNow) {
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
        val canCaptureMobileState = disconnected &&
            snapshot.rememberedMobileDataEnabledBeforeAirplaneMode == null &&
            networkControlProbe.probeNetworkControlState(NetworkControlMode.AirplaneMode).controlledEnabled == false
        val mobileDataStateToRemember = snapshot.rememberedMobileDataEnabledBeforeAirplaneMode
            ?: if (canCaptureMobileState) networkControlProbe.probeNetworkControlState(NetworkControlMode.MobileData).controlledEnabled else null
        // Persist before enabling airplane mode: cancellation can happen after the
        // radio changes but before the command's readback returns.
        if (canCaptureMobileState && mobileDataStateToRemember != null) {
            runtimeStatusRepository.updateSnapshot { it.copy(rememberedMobileDataEnabledBeforeAirplaneMode = mobileDataStateToRemember) }
        }
        val airplaneResult = networkControlProbe.setNetworkControlEnabled(
            mode = NetworkControlMode.AirplaneMode,
            enabled = disconnected,
        )
        if (canCaptureMobileState && airplaneResult.controlledEnabled == false) {
            runtimeStatusRepository.updateSnapshot { it.copy(rememberedMobileDataEnabledBeforeAirplaneMode = null) }
        }
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
        val action = formatter.actionFor(mode, result.requestedEnabled ?: result.controlledEnabled)
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
