package com.gaozay.smartflight.runtime

import javax.inject.Inject

class DisconnectAutomationHandler @Inject constructor(
    private val screenOffDisconnectHandler: ScreenOffDisconnectHandler,
    private val appExitDisconnectHandler: AppExitDisconnectHandler,
) {
    suspend fun scheduleScreenOffDisconnectIfNeeded(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        screenOffDisconnectHandler.scheduleIfNeeded(state, scheduler)
    }

    suspend fun handleScreenOffDisconnectDue(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        screenOffDisconnectHandler.handleDue(state, scheduler)
    }

    suspend fun scheduleAppExitDisconnect(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        reason: String,
        delaySeconds: Int,
        baseTimestampMillis: Long = System.currentTimeMillis(),
    ) {
        appExitDisconnectHandler.schedule(
            state = state,
            scheduler = scheduler,
            reason = reason,
            delaySeconds = delaySeconds,
            baseTimestampMillis = baseTimestampMillis,
        )
    }

    suspend fun handleAppExitDisconnectDue(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
    ) {
        appExitDisconnectHandler.handleDue(state, scheduler)
    }

    companion object {
        fun remainingDelayMillis(
            delaySeconds: Int,
            baseTimestampMillis: Long,
            nowMillis: Long,
        ): Long = DisconnectDelayCalculator.remainingDelayMillis(
            delaySeconds = delaySeconds,
            baseTimestampMillis = baseTimestampMillis,
            nowMillis = nowMillis,
        )
    }
}
