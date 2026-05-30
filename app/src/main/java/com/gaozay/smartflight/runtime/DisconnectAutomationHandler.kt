package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DisconnectAutomationHandler @Inject constructor(
    private val accessRepository: AccessRepository,
    private val automationRuleEngine: AutomationRuleEngine,
    private val reporter: RuntimeSnapshotReporter,
    private val networkChangeExecutor: RuntimeNetworkChangeExecutor,
) {
    suspend fun scheduleScreenOffDisconnectIfNeeded(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        val settings = state.settings
        if (!settings.isAutomationEffectivelyEnabled()) {
            Log.d(LOG_TAG, "skip screen-off schedule: automation temporarily disabled")
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
            return
        }
        val runtimeSnapshot = reporter.snapshot.first()
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        if (!automationRuleEngine.shouldScheduleScreenOffDisconnect(
                settings = settings,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
            )
        ) {
            Log.d(LOG_TAG, "skip screen-off schedule: conditions not met")
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
            return
        }
        if (runtimeSnapshot.isDisconnected(settings.networkControlMode) == true) {
            Log.d(LOG_TAG, "skip screen-off schedule: already disconnected")
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
            return
        }
        if (scheduler.isScreenOffDisconnectActive) {
            Log.d(LOG_TAG, "skip screen-off schedule: job already active")
            return
        }
        val delayMillis = settings.screenOffDelaySeconds.coerceAtLeast(0) * 1_000L
        val executeAtMillis = System.currentTimeMillis() + delayMillis
        reporter.update { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = true,
                pendingScreenOffDisconnectAtMillis = executeAtMillis,
                lastAction = ExecutionAction.ScheduleScreenOffDisconnect,
                lastTriggerSource = TriggerSource.ScreenOff,
                lastActionResult = ExecutionResult.Pending,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        Log.d(
            LOG_TAG,
            "scheduled screen-off disconnect delay=${settings.screenOffDelaySeconds}s executeAt=$executeAtMillis now=${System.currentTimeMillis()}",
        )
        scheduler.scheduleScreenOffDisconnect(delayMillis)
    }

    suspend fun handleScreenOffDisconnectDue(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        scheduler.markScreenOffDisconnectConsumed()
        val latestSettings = state.settings
        if (!latestSettings.isAutomationEffectivelyEnabled()) {
            Log.d(LOG_TAG, "skip screen-off execution after delay: automation temporarily disabled")
            reporter.update { snapshot ->
                snapshot.copy(
                    isScreenOffDisconnectScheduled = false,
                    pendingScreenOffDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }
        val latestRuntimeSnapshot = reporter.snapshot.first()
        val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val shouldDisconnect = automationRuleEngine.shouldExecuteScreenOffDisconnect(
            settings = latestSettings,
            screenState = state.screenState,
            isWifiConnected = latestRuntimeSnapshot.isWifiConnected,
            executorAvailable = latestExecutorAvailable,
        ) && latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode) != true
        reporter.update { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        if (!shouldDisconnect) {
            Log.d(LOG_TAG, "skip screen-off execution after delay: conditions changed")
            return
        }
        Log.d(LOG_TAG, "execute screen-off disconnect after delay")
        networkChangeExecutor.executeNetworkChange(
            scheduler = scheduler,
            currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
            targetDisconnected = true,
            triggerSource = TriggerSource.ScreenOff,
            reason = "息屏延迟 ${latestSettings.screenOffDelaySeconds} 秒后执行断网${latestSettings.mobileDataNoOpSuffix()}",
            prompt = RuntimePrompt.Disconnect(latestSettings),
        )
    }

    suspend fun scheduleAppExitDisconnect(
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
        val delayMillis = remainingDelayMillis(
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

    suspend fun handleAppExitDisconnectDue(
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

    companion object {
        const val LOG_TAG = "SmartFlightRuntime"

        fun remainingDelayMillis(
            delaySeconds: Int,
            baseTimestampMillis: Long,
            nowMillis: Long,
        ): Long {
            val totalDelayMillis = delaySeconds.coerceAtLeast(0) * 1_000L
            val effectiveBaseMillis = baseTimestampMillis.coerceAtMost(nowMillis)
            val elapsedMillis = (nowMillis - effectiveBaseMillis).coerceAtLeast(0L)
            return (totalDelayMillis - elapsedMillis).coerceAtLeast(0L)
        }
    }
}
