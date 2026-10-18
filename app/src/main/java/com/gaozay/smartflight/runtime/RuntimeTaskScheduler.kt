package com.gaozay.smartflight.runtime

import android.os.SystemClock
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
    private val monotonicTimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private var foregroundProbeJob: Job? = null
    private var screenOffDisconnectJob: Job? = null
    private var appExitDisconnectJob: Job? = null
    private var temporaryDisableExpiryJob: Job? = null
    private var foregroundGeneration = 0L
    private var foregroundTask: Long? = null
    private var expiryGeneration = 0L
    private var expiryTask: Long? = null
    private var disconnectGeneration = 0L
    private var appExitTask: DisconnectTaskToken? = null
    private var screenOffTask: DisconnectTaskToken? = null

    // A task remains pending while its Due event waits in the serialized event queue.
    val isAppExitDisconnectActive: Boolean
        get() = appExitTask != null

    val isScreenOffDisconnectActive: Boolean
        get() = screenOffTask != null

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
        val generation = ++foregroundGeneration
        foregroundTask = generation
        foregroundProbeJob = schedule(delayMillis, RuntimeEvent.ForegroundProbeTick(generation))
    }

    fun markForegroundProbeConsumed(event: RuntimeEvent.ForegroundProbeTick): Boolean {
        if (foregroundTask != event.generation) return false
        foregroundTask = null
        foregroundProbeJob = null
        return true
    }

    fun cancelForegroundProbe() {
        foregroundTask = null
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
    }

    fun scheduleTemporaryDisableExpiry(untilMillis: Long) {
        cancelTemporaryDisableExpiry()
        val generation = ++expiryGeneration
        expiryTask = generation
        temporaryDisableExpiryJob = schedule(
            delayMillis = (untilMillis - System.currentTimeMillis()).coerceAtLeast(0L),
            event = RuntimeEvent.TemporaryDisableExpired(generation),
        )
    }

    fun markTemporaryDisableExpiryConsumed(event: RuntimeEvent.TemporaryDisableExpired): Boolean {
        if (expiryTask != event.generation) return false
        expiryTask = null
        temporaryDisableExpiryJob = null
        return true
    }

    fun cancelTemporaryDisableExpiry() {
        expiryTask = null
        temporaryDisableExpiryJob?.cancel()
        temporaryDisableExpiryJob = null
    }

    fun scheduleScreenOffDisconnect(delayMillis: Long) {
        cancelScreenOffDisconnect()
        val task = newDisconnectTask(delayMillis)
        screenOffTask = task
        screenOffDisconnectJob = schedule(delayMillis, RuntimeEvent.ScreenOffDisconnectDue(task))
    }

    fun isScreenOffDisconnectDueCurrent(event: RuntimeEvent.ScreenOffDisconnectDue): Boolean =
        screenOffTask == event.task && monotonicTimeMillis() >= event.task.deadlineMillis

    fun markScreenOffDisconnectConsumed(event: RuntimeEvent.ScreenOffDisconnectDue): Boolean {
        if (!isScreenOffDisconnectDueCurrent(event)) return false
        screenOffTask = null
        screenOffDisconnectJob = null
        return true
    }

    fun cancelScreenOffDisconnect(): Boolean {
        val wasScheduled = screenOffTask != null
        screenOffDisconnectJob?.cancel()
        screenOffDisconnectJob = null
        screenOffTask = null
        return wasScheduled
    }

    fun scheduleAppExitDisconnect(delayMillis: Long) {
        cancelAppExitDisconnect()
        val task = newDisconnectTask(delayMillis)
        appExitTask = task
        appExitDisconnectJob = schedule(delayMillis, RuntimeEvent.AppExitDisconnectDue(task))
    }

    fun isAppExitDisconnectDueCurrent(event: RuntimeEvent.AppExitDisconnectDue): Boolean =
        appExitTask == event.task && monotonicTimeMillis() >= event.task.deadlineMillis

    fun markAppExitDisconnectConsumed(event: RuntimeEvent.AppExitDisconnectDue): Boolean {
        if (!isAppExitDisconnectDueCurrent(event)) return false
        appExitTask = null
        appExitDisconnectJob = null
        return true
    }

    fun cancelAppExitDisconnect(): Boolean {
        val wasScheduled = appExitTask != null
        appExitDisconnectJob?.cancel()
        appExitDisconnectJob = null
        appExitTask = null
        return wasScheduled
    }

    private fun newDisconnectTask(delayMillis: Long): DisconnectTaskToken =
        DisconnectTaskToken(++disconnectGeneration, monotonicTimeMillis() + delayMillis.coerceAtLeast(0L))

    suspend fun clearPendingDisconnects() {
        cancelScreenOffDisconnect()
        cancelAppExitDisconnect()
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
