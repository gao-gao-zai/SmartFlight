package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import com.gaozay.smartflight.settings.withAutomationDisabled
import javax.inject.Inject

class UnexpectedNetworkChangeGuard @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reporter: RuntimeSnapshotReporter,
    private val promptNotifier: RuntimePromptNotifier,
    private val expectedNetworkChangeTracker: RuntimeExpectedNetworkChangeTracker,
) {
    suspend fun handleNetworkStateObserved(
        state: RuntimeState,
        previousSnapshot: RuntimeSnapshot,
        updatedSnapshot: RuntimeSnapshot,
    ): RuntimeState {
        val settings = state.settings
        if (!settings.automationEnabled || settings.isTemporaryDisableActive()) {
            expectedNetworkChangeTracker.clearExpired()
            return state
        }
        val previousDisconnected = previousSnapshot.isDisconnected(settings.networkControlMode)
        val updatedDisconnected = updatedSnapshot.isDisconnected(settings.networkControlMode)
        if (previousDisconnected == null || updatedDisconnected == null) {
            expectedNetworkChangeTracker.clearExpired()
            return state
        }
        if (previousDisconnected == updatedDisconnected) {
            expectedNetworkChangeTracker.clearExpired()
            return state
        }
        if (expectedNetworkChangeTracker.consumeIfExpected(updatedDisconnected)) {
            Log.d(LOG_TAG, "ignore expected network control change disconnected=$updatedDisconnected")
            return state
        }

        val reason = "检测到联网状态被外部改变，已暂停自动化直到应用切换"
        val foregroundPackageName = state.lastKnownForegroundApp?.packageName
            ?: updatedSnapshot.currentForegroundPackageName
            ?: UNKNOWN_FOREGROUND_PACKAGE
        val restoredState = state.copy(
            settings = settings.withAutomationDisabled(
                mode = AutomationDisableMode.UntilAppSwitch,
                foregroundPackageName = foregroundPackageName,
            ),
        )
        settingsRepository.updateSettings {
            it.withAutomationDisabled(
                mode = AutomationDisableMode.UntilAppSwitch,
                foregroundPackageName = foregroundPackageName,
            )
        }
        reporter.markTemporaryDisabled(
            triggerSource = com.gaozay.smartflight.domain.model.TriggerSource.SettingsChanged,
            reason = reason,
        )
        promptNotifier.showAutomationPausedPrompt(restoredState.settings, reason)
        Log.d(
            LOG_TAG,
            "unexpected network control change previous=$previousDisconnected updated=$updatedDisconnected foreground=$foregroundPackageName",
        )
        return restoredState
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
        const val UNKNOWN_FOREGROUND_PACKAGE = "__smartflight_unknown_foreground__"
    }
}
