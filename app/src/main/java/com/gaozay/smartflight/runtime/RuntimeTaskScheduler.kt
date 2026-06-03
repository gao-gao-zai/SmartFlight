package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RuntimeTaskScheduler(
    private val scope: CoroutineScope,
    private val send: (RuntimeEvent) -> Unit,
    private val reporter: RuntimeSnapshotReporter,
) {
    private var foregroundProbeJob: Job? = null
    private var screenOffDisconnectJob: Job? = null
    private var appExitDisconnectJob: Job? = null
    private var temporaryDisableExpiryJob: Job? = null

    val isAppExitDisconnectActive: Boolean
        get() = appExitDisconnectJob?.isActive == true

    val isScreenOffDisconnectActive: Boolean
        get() = screenOffDisconnectJob?.isActive == true

    fun scheduleForegroundProbe(
        state: RuntimeState,
        automationRuleEngine: AutomationRuleEngine,
        immediate: Boolean,
        eventDrivenForegroundAvailable: Boolean = false,
    ) {
        cancelForegroundProbe()
        if (!state.settings.automationEnabled) {
            return
        }
        if (!automationRuleEngine.shouldMonitorForeground(state.settings, state.screenState)) {
            return
        }
        if (eventDrivenForegroundAvailable && !immediate) {
            return
        }
        val delayMillis = if (immediate) 0L else foregroundProbeIntervalMillis(state.screenState)
        foregroundProbeJob = schedule(delayMillis, RuntimeEvent.ForegroundProbeTick)
    }

    fun markForegroundProbeConsumed() {
        foregroundProbeJob = null
    }

    fun cancelForegroundProbe() {
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
    }

    fun scheduleTemporaryDisableExpiry(untilMillis: Long) {
        temporaryDisableExpiryJob?.cancel()
        temporaryDisableExpiryJob = schedule(
            delayMillis = (untilMillis - System.currentTimeMillis()).coerceAtLeast(0L),
            event = RuntimeEvent.TemporaryDisableExpired,
        )
    }

    fun markTemporaryDisableExpiryConsumed() {
        temporaryDisableExpiryJob = null
    }

    fun cancelTemporaryDisableExpiry() {
        temporaryDisableExpiryJob?.cancel()
        temporaryDisableExpiryJob = null
    }

    fun scheduleScreenOffDisconnect(delayMillis: Long) {
        screenOffDisconnectJob = schedule(delayMillis, RuntimeEvent.ScreenOffDisconnectDue)
    }

    fun markScreenOffDisconnectConsumed() {
        screenOffDisconnectJob = null
    }

    fun cancelScreenOffDisconnect(): Boolean {
        val wasScheduled = screenOffDisconnectJob?.isActive == true
        screenOffDisconnectJob?.cancel()
        screenOffDisconnectJob = null
        return wasScheduled
    }

    fun scheduleAppExitDisconnect(delayMillis: Long) {
        appExitDisconnectJob = schedule(delayMillis, RuntimeEvent.AppExitDisconnectDue)
    }

    fun markAppExitDisconnectConsumed() {
        appExitDisconnectJob = null
    }

    fun cancelAppExitDisconnect(): Boolean {
        val wasScheduled = appExitDisconnectJob?.isActive == true
        appExitDisconnectJob?.cancel()
        appExitDisconnectJob = null
        return wasScheduled
    }

    suspend fun clearPendingDisconnects() {
        screenOffDisconnectJob?.cancel()
        screenOffDisconnectJob = null
        appExitDisconnectJob?.cancel()
        appExitDisconnectJob = null
        reporter.update { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun cancelAll() {
        cancelForegroundProbe()
        cancelScreenOffDisconnect()
        cancelAppExitDisconnect()
        cancelTemporaryDisableExpiry()
        reporter.update { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun schedule(delayMillis: Long, event: RuntimeEvent): Job =
        scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            send(event)
        }

    companion object {
        const val SCREEN_ON_POLL_INTERVAL_MILLIS = 1_500L
        const val SCREEN_OFF_POLL_INTERVAL_MILLIS = 8_000L

        fun foregroundProbeIntervalMillis(screenState: ScreenState): Long =
            when (screenState) {
                ScreenState.ScreenOff -> SCREEN_OFF_POLL_INTERVAL_MILLIS
                ScreenState.ScreenOn, ScreenState.Unlocked, ScreenState.Unknown -> SCREEN_ON_POLL_INTERVAL_MILLIS
            }
    }
}

suspend fun RuntimeTaskScheduler.cancelScreenOffDisconnect(
    state: RuntimeState,
    reporter: RuntimeSnapshotReporter,
    updateRuntimeState: Boolean,
) {
    val wasScheduled = cancelScreenOffDisconnect()
    if (!updateRuntimeState && !wasScheduled) {
        return
    }
    reporter.update { snapshot ->
        snapshot.copy(
            isScreenOffDisconnectScheduled = false,
            pendingScreenOffDisconnectAtMillis = null,
            lastAction = if (updateRuntimeState) ExecutionAction.CancelScheduledDisconnect else snapshot.lastAction,
            lastTriggerSource = if (updateRuntimeState) {
                when (state.screenState) {
                    ScreenState.Unlocked -> TriggerSource.UserUnlocked
                    ScreenState.ScreenOn -> TriggerSource.ScreenOn
                    else -> TriggerSource.Manual
                }
            } else {
                snapshot.lastTriggerSource
            },
            lastActionResult = if (updateRuntimeState) {
                if (wasScheduled) ExecutionResult.Success else ExecutionResult.Skipped
            } else {
                snapshot.lastActionResult
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}

suspend fun RuntimeTaskScheduler.cancelAppExitDisconnect(
    reporter: RuntimeSnapshotReporter,
    updateRuntimeState: Boolean,
) {
    val wasScheduled = cancelAppExitDisconnect()
    if (!updateRuntimeState) {
        reporter.update { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        return
    }
    reporter.update { snapshot ->
        snapshot.copy(
            isAppExitDisconnectScheduled = false,
            pendingAppExitDisconnectAtMillis = null,
            lastAction = ExecutionAction.CancelScheduledDisconnect,
            lastTriggerSource = TriggerSource.AppForegroundChanged,
            lastActionResult = if (wasScheduled) ExecutionResult.Success else ExecutionResult.Skipped,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}
