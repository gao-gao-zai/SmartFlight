package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ForegroundAutomationHandler @Inject constructor(
    private val accessRepository: AccessRepository,
    private val foregroundAppSource: ForegroundAppSource,
    private val automationRuleEngine: AutomationRuleEngine,
    private val reporter: RuntimeSnapshotReporter,
    private val networkChangeExecutor: RuntimeNetworkChangeExecutor,
    private val disconnectAutomationHandler: DisconnectAutomationHandler,
    private val temporaryDisableHandler: TemporaryDisableHandler,
) {
    suspend fun automationTick(
        state: RuntimeState,
        scheduler: RuntimeTaskScheduler,
        triggerSource: TriggerSource = TriggerSource.AppForegroundChanged,
        allowReconnectWhenTargetAppAlreadyActive: Boolean = false,
    ): RuntimeState {
        var currentState = state
        val settings = currentState.settings
        Log.d(
            LOG_TAG,
            "automationTick trigger=$triggerSource screenState=${currentState.screenState} allowWakeReconnect=$allowReconnectWhenTargetAppAlreadyActive monitorWhenScreenOff=${settings.monitorForegroundWhenScreenOff} reconnectOnLaunch=${settings.reconnectOnTargetAppLaunch}",
        )
        if (!automationRuleEngine.shouldMonitorForeground(settings, currentState.screenState)) {
            Log.d(LOG_TAG, "automationTick skipped: shouldMonitorForeground=false screenState=${currentState.screenState}")
            reporter.markServiceRunning(currentState.screenState)
            return currentState
        }
        val foregroundApp = foregroundAppSource.detect()
        currentState = currentState.copy(lastKnownForegroundApp = foregroundApp ?: currentState.lastKnownForegroundApp)
        reporter.markForegroundApp(foregroundApp, currentState.screenState)

        val packageName = foregroundApp?.packageName
        if (settings.isTemporaryDisableActive()) {
            if (settings.temporaryDisableMode == AutomationDisableMode.UntilAppSwitch &&
                packageName != null &&
                settings.temporaryDisableForegroundPackageName != null &&
                packageName != settings.temporaryDisableForegroundPackageName
            ) {
                currentState = temporaryDisableHandler.clearTemporaryDisable(
                    state = currentState,
                    scheduler = scheduler,
                    reason = "检测到应用切换，恢复自动化",
                )
            } else {
                reporter.markTemporaryDisabled(
                    triggerSource = triggerSource,
                    reason = settings.temporaryDisableMode.label,
                )
                return currentState
            }
        }
        val effectiveSettings = currentState.settings
        val runtimeSnapshot = reporter.snapshot.first()
        val appRuleInfo = packageName?.let { currentState.appRulesByPackageName[it] }
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val isDisconnected = runtimeSnapshot.isDisconnected(effectiveSettings.networkControlMode)
        Log.d(
            LOG_TAG,
            "automationTick snapshot pkg=${packageName ?: "<none>"} online=${appRuleInfo?.isInOnlineList == true} blacklist=${appRuleInfo?.isInBlacklist == true} executorAvailable=$executorAvailable disconnected=$isDisconnected wifi=${runtimeSnapshot.isWifiConnected} lastTarget=${currentState.lastTargetAppActive}",
        )
        val decision = automationRuleEngine.evaluateForegroundChange(
            ForegroundRuleContext(
                settings = effectiveSettings,
                packageName = packageName,
                appLabel = foregroundApp?.appLabel,
                isInOnlineList = appRuleInfo?.isInOnlineList == true,
                isInBlacklist = appRuleInfo?.isInBlacklist == true,
                onlineSource = appRuleInfo?.sourceTag,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
                previousTargetAppActive = currentState.lastTargetAppActive,
                isCurrentlyDisconnected = isDisconnected,
                isAppExitDisconnectScheduled = scheduler.isAppExitDisconnectActive,
                allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
            ),
        )
        Log.d(
            LOG_TAG,
            buildString {
                append("foreground decision")
                append(" pkg=")
                append(packageName ?: "<none>")
                append(" label=")
                append(foregroundApp?.appLabel ?: "<none>")
                append(" prevTarget=")
                append(currentState.lastTargetAppActive)
                append(" target=")
                append(decision.targetAppActive)
                append(" disconnected=")
                append(isDisconnected)
                append(" appExitScheduled=")
                append(scheduler.isAppExitDisconnectActive)
                append(" screenOffScheduled=")
                append(scheduler.isScreenOffDisconnectActive)
                append(" delay=")
                append(effectiveSettings.appExitDelaySeconds)
                append("s action=")
                append(decision.action::class.simpleName)
                append(" rules=")
                append(decision.matchedRules.joinToString(","))
                append(" reason=")
                append(decision.reason)
            },
        )
        currentState = currentState.copy(lastTargetAppActive = decision.targetAppActive)

        when (val action = decision.action) {
            is ForegroundAction.None -> {
                if (decision.shouldLog) {
                    reporter.markSkippedForegroundDecision(decision, triggerSource)
                }
            }
            is ForegroundAction.Reconnect -> {
                scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = true)
                networkChangeExecutor.executeNetworkChange(
                    scheduler = scheduler,
                    currentDisconnected = isDisconnected,
                    targetDisconnected = false,
                    triggerSource = triggerSource,
                    reason = action.reason + effectiveSettings.mobileDataNoOpSuffix(),
                    prompt = RuntimePrompt.Reconnect(effectiveSettings),
                )
            }
            is ForegroundAction.Disconnect -> {
                scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = false)
                networkChangeExecutor.executeNetworkChange(
                    scheduler = scheduler,
                    currentDisconnected = isDisconnected,
                    targetDisconnected = true,
                    triggerSource = triggerSource,
                    reason = action.reason + effectiveSettings.mobileDataNoOpSuffix(),
                    prompt = RuntimePrompt.Disconnect(effectiveSettings),
                )
            }
            is ForegroundAction.ScheduleDisconnect -> {
                val eventTimestampMillis = foregroundApp?.eventTimestampMillis ?: System.currentTimeMillis()
                disconnectAutomationHandler.scheduleAppExitDisconnect(
                    state = currentState,
                    scheduler = scheduler,
                    reason = action.reason,
                    delaySeconds = action.delaySeconds,
                    baseTimestampMillis = eventTimestampMillis,
                )
            }
            is ForegroundAction.CancelScheduledDisconnect -> {
                scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = true)
            }
            is ForegroundAction.PauseAutomation -> {
                reporter.markPauseAutomationFailed(action.reason, triggerSource)
            }
        }
        return currentState
    }

    private companion object {
        const val LOG_TAG = "SmartFlightRuntime"
    }
}
