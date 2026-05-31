package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScreenOffDisconnectHandler @Inject constructor(
    private val accessRepository: AccessRepository,
    private val automationRuleEngine: AutomationRuleEngine,
    private val reporter: RuntimeSnapshotReporter,
    private val networkChangeExecutor: RuntimeNetworkChangeExecutor,
) {
    suspend fun scheduleIfNeeded(
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

    suspend fun handleDue(
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

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}
