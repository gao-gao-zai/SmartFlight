package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RuntimeNetworkChangeExecutor @Inject constructor(
    private val accessRepository: AccessRepository,
    private val reporter: RuntimeSnapshotReporter,
    private val promptNotifier: RuntimePromptNotifier,
) {
    suspend fun executeNetworkChange(
        scheduler: RuntimeTaskScheduler,
        currentDisconnected: Boolean?,
        targetDisconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String,
        prompt: RuntimePrompt,
    ) {
        if (currentDisconnected == targetDisconnected) {
            Log.d(
                LOG_TAG,
                "skip network change: already target state trigger=$triggerSource current=$currentDisconnected target=$targetDisconnected reason=$reason",
            )
            return
        }
        Log.d(
            LOG_TAG,
            "execute network change trigger=$triggerSource current=$currentDisconnected target=$targetDisconnected reason=$reason",
        )
        accessRepository.setDisconnectedState(
            disconnected = targetDisconnected,
            triggerSource = triggerSource,
            reason = reason,
        )
        val updatedSnapshot = reporter.snapshot.first()
        Log.d(
            LOG_TAG,
            "network change result trigger=$triggerSource result=${updatedSnapshot.lastActionResult} action=${updatedSnapshot.lastAction} reason=${updatedSnapshot.lastActionReason}",
        )
        if (updatedSnapshot.lastActionResult == ExecutionResult.Success) {
            if (targetDisconnected) {
                scheduler.clearPendingDisconnects()
                Log.d(LOG_TAG, "cleared pending disconnect jobs")
            }
            when (prompt) {
                is RuntimePrompt.Reconnect -> promptNotifier.showReconnectPrompt(prompt.settings)
                is RuntimePrompt.Disconnect -> promptNotifier.showDisconnectPrompt(prompt.settings)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}

sealed interface RuntimePrompt {
    val settings: com.gaozay.smartflight.settings.UserSettings

    data class Reconnect(
        override val settings: com.gaozay.smartflight.settings.UserSettings,
    ) : RuntimePrompt

    data class Disconnect(
        override val settings: com.gaozay.smartflight.settings.UserSettings,
    ) : RuntimePrompt
}
