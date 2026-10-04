package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject

class RuntimeSnapshotReporter @Inject constructor(
    private val runtimeStatusRepository: RuntimeStatusRepository,
) {
    val snapshot = runtimeStatusRepository.snapshot

    suspend fun update(transform: (RuntimeSnapshot) -> RuntimeSnapshot) {
        runtimeStatusRepository.updateSnapshot(transform)
    }

    suspend fun reportEventFailure(throwable: Throwable) {
        update { snapshot ->
            snapshot.copy(
                lastTriggerSource = TriggerSource.ServiceRestored,
                lastActionResult = ExecutionResult.Failed,
                lastActionReason = AppStrings.get(R.string.automation_event_handling_failed, throwable.message ?: AppStrings.get(R.string.unknown_error)),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markServiceStarted(initialScreenState: ScreenState) {
        update { snapshot ->
            snapshot.copy(
                isForegroundServiceRunning = true,
                screenState = initialScreenState,
                lastTriggerSource = TriggerSource.ServiceRestored,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markServiceStopped(finalScreenState: ScreenState) {
        update { snapshot ->
            snapshot.copy(
                isForegroundServiceRunning = false,
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                screenState = finalScreenState,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markServiceRunning(screenState: ScreenState? = null) {
        update { snapshot ->
            snapshot.copy(
                screenState = screenState ?: snapshot.screenState,
                isForegroundServiceRunning = true,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markForegroundApp(foregroundApp: ForegroundAppInfo?, screenState: ScreenState) {
        update { snapshot ->
            snapshot.copy(
                currentForegroundPackageName = foregroundApp?.packageName,
                currentForegroundAppLabel = foregroundApp?.appLabel,
                currentForegroundActivityName = foregroundApp?.confirmedActivity(),
                foregroundEventTimestampMillis = foregroundApp?.eventTimestampMillis ?: 0,
                foregroundInfoSource = foregroundApp?.source?.name ?: "Unknown",
                foregroundActivityConfirmed = foregroundApp?.confirmedActivity() != null,
                screenState = screenState,
                isForegroundServiceRunning = true,
                updatedAtMillis = if (foregroundApp != null) System.currentTimeMillis() else snapshot.updatedAtMillis,
            )
        }
    }

    suspend fun markForegroundRule(resolved: ResolvedForegroundRule) {
        update { it.copy(foregroundRuleLayer = if (resolved.reason == ActivityMatchReason.ActivityOverride) "Activity" else "App",
            foregroundRuleReason = resolved.reason.name) }
    }

    suspend fun markTemporaryDisabled(triggerSource: TriggerSource, reason: String) {
        update { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.PauseAutomation,
                lastTriggerSource = triggerSource,
                lastActionResult = ExecutionResult.Pending,
                lastActionReason = reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markTemporaryDisableCleared(reason: String) {
        update { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.PauseAutomation,
                lastTriggerSource = TriggerSource.SettingsChanged,
                lastActionResult = ExecutionResult.Success,
                lastActionReason = reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markSkippedForegroundDecision(
        decision: ForegroundRuleDecision,
        triggerSource: TriggerSource,
    ) {
        update { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.DoNothing,
                lastTriggerSource = triggerSource,
                lastActionResult = ExecutionResult.Skipped,
                lastActionReason = decision.reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markPauseAutomationFailed(reason: String, triggerSource: TriggerSource) {
        update { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.PauseAutomation,
                lastTriggerSource = triggerSource,
                lastActionResult = ExecutionResult.Failed,
                lastActionReason = reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }
}
