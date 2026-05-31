package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.withTemporaryDisableCleared
import javax.inject.Inject

class TemporaryDisableHandler @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reporter: RuntimeSnapshotReporter,
    private val promptNotifier: RuntimePromptNotifier,
) {
    fun scheduleTemporaryDisableExpiry(
        scheduler: RuntimeTaskScheduler,
        settings: UserSettings,
    ) {
        val untilMillis = settings.temporaryDisableUntilMillis ?: return
        scheduler.scheduleTemporaryDisableExpiry(untilMillis)
    }

    suspend fun clearTemporaryDisable(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        reason: String,
    ): RuntimeState {
        val restoredSettings = state.settings.withTemporaryDisableCleared()
        scheduler.cancelTemporaryDisableExpiry()
        settingsRepository.updateSettings { it.withTemporaryDisableCleared() }
        reporter.markTemporaryDisableCleared(reason)
        promptNotifier.showAutomationRestoredPrompt(restoredSettings, reason)
        Log.d(LOG_TAG, "temporary disable cleared: $reason")
        return state.copy(settings = restoredSettings)
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}
