package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class AppExitDisconnectHandler internal constructor(
    private val accessRepository: AccessRepository,
    private val reporter: RuntimeSnapshotReporter,
    private val networkChangeExecutor: RuntimeNetworkChangeExecutor,
    private val wallTimeMillis: () -> Long,
) {
    @Inject constructor(
        accessRepository: AccessRepository,
        reporter: RuntimeSnapshotReporter,
        networkChangeExecutor: RuntimeNetworkChangeExecutor,
    ) : this(accessRepository, reporter, networkChangeExecutor, System::currentTimeMillis)

    suspend fun schedule(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        reason: String,
        delaySeconds: Int,
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
        // Every accepted exit/rule edit starts a fresh full countdown. Wall time
        // is only a display timestamp; elapsed time and clock corrections must
        // not shorten the monotonic task's duration.
        val delayMillis = delaySeconds.coerceAtLeast(0) * 1_000L
        val nowMillis = wallTimeMillis()
        val executeAtMillis = nowMillis + delayMillis
        reporter.update { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = true,
                pendingAppExitDisconnectAtMillis = executeAtMillis,
                lastAction = ExecutionAction.ScheduleAppExitDisconnect,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
                lastActionResult = ExecutionResult.Pending,
                updatedAtMillis = nowMillis,
            )
        }
        Log.d(
            LOG_TAG,
            "scheduled app-exit disconnect delay=${delaySeconds}s now=$nowMillis remainingMs=$delayMillis executeAt=$executeAtMillis reason=$reason",
        )
        scheduler.scheduleAppExitDisconnect(delayMillis)
    }

    suspend fun handleDue(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        event: RuntimeEvent.AppExitDisconnectDue,
    ) {
        if (!scheduler.markAppExitDisconnectConsumed(event)) return
        val latestSettings = state.settings
        val latestRuntimeSnapshot = reporter.snapshot.first()
        val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val shouldDisconnect = latestSettings.isAutomationEffectivelyEnabled() &&
            latestSettings.appExitDisconnectEnabled &&
            state.lastTargetAppActive == false &&
            state.lastKnownForegroundApp != null &&
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
            reason = AppStrings.get(R.string.an_online_app_left_the_foreground, latestSettings.mobileDataNoOpSuffix()),
            prompt = RuntimePrompt.Disconnect(latestSettings),
        )
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}
