package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AppExitDisconnectHandler @Inject constructor(
    private val accessRepository: AccessRepository,
    private val reporter: RuntimeSnapshotReporter,
    private val networkChangeExecutor: RuntimeNetworkChangeExecutor,
) {
    suspend fun schedule(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        reason: String,
        delaySeconds: Int,
        baseTimestampMillis: Long = System.currentTimeMillis(),
    ) {
        val runtimeSnapshot = reporter.snapshot.first()
        val currentSettings = state.settings
        if (runtimeSnapshot.isDisconnected(currentSettings.networkControlMode) == true) {
            Log.d(LOG_TAG, "skip app-exit schedule: already disconnected")
            scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = false)
            return
        }
        if (scheduler.isAppExitDisconnectActive) {
            Log.d(LOG_TAG, "skip app-exit schedule: job already active")
            return
        }
        val delayMillis = DisconnectDelayCalculator.remainingDelayMillis(
            delaySeconds = delaySeconds,
            baseTimestampMillis = baseTimestampMillis,
            nowMillis = System.currentTimeMillis(),
        )
        val executeAtMillis = System.currentTimeMillis() + delayMillis
        reporter.update { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = true,
                pendingAppExitDisconnectAtMillis = executeAtMillis,
                lastAction = ExecutionAction.ScheduleAppExitDisconnect,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
                lastActionResult = ExecutionResult.Pending,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        Log.d(
            LOG_TAG,
            "scheduled app-exit disconnect delay=${delaySeconds}s base=$baseTimestampMillis now=${System.currentTimeMillis()} remainingMs=$delayMillis executeAt=$executeAtMillis reason=$reason",
        )
        scheduler.scheduleAppExitDisconnect(delayMillis)
    }

    suspend fun handleDue(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        scheduler.markAppExitDisconnectConsumed()
        val latestSettings = state.settings
        val latestRuntimeSnapshot = reporter.snapshot.first()
        val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val shouldDisconnect = latestSettings.isAutomationEffectivelyEnabled() &&
            latestSettings.appExitDisconnectEnabled &&
            state.lastTargetAppActive == false &&
            latestExecutorAvailable &&
            latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode) != true &&
            !(latestRuntimeSnapshot.isWifiConnected && latestSettings.skipDisconnectOnWifi)
        reporter.update { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        if (!shouldDisconnect) {
            Log.d(
                LOG_TAG,
                "skip app-exit execution after delay: targetActive=${state.lastTargetAppActive} disconnected=${latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode)} wifi=${latestRuntimeSnapshot.isWifiConnected}",
            )
            return
        }
        Log.d(LOG_TAG, "execute app-exit disconnect after delay")
        networkChangeExecutor.executeNetworkChange(
            scheduler = scheduler,
            currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
            targetDisconnected = true,
            triggerSource = TriggerSource.AppForegroundChanged,
            reason = "联网应用已离开前台${latestSettings.mobileDataNoOpSuffix()}",
            prompt = RuntimePrompt.Disconnect(latestSettings),
        )
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}
