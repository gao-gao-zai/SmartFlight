package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRuleEngine @Inject constructor() {
    fun shouldMonitorForeground(
        settings: UserSettings,
        screenState: ScreenState,
    ): Boolean = settings.monitorForegroundWhenScreenOff || screenState != ScreenState.ScreenOff

    fun nextPollIntervalMillis(
        settings: UserSettings,
        screenState: ScreenState,
    ): Long {
        if (screenState == ScreenState.ScreenOff && !settings.monitorForegroundWhenScreenOff) {
            return SCREEN_OFF_IDLE_POLL_INTERVAL_MILLIS
        }
        return when (screenState) {
            ScreenState.ScreenOff -> SCREEN_OFF_POLL_INTERVAL_MILLIS
            ScreenState.ScreenOn, ScreenState.Unlocked, ScreenState.Unknown -> SCREEN_ON_POLL_INTERVAL_MILLIS
        }
    }

    fun evaluateForegroundChange(context: ForegroundRuleContext): ForegroundRuleDecision {
        val targetAppActive = context.packageName != null && context.packageName in context.whitelistPackages
        if (context.previousTargetAppActive == targetAppActive) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.None,
            )
        }
        if (targetAppActive && context.settings.reconnectOnTargetAppLaunch) {
            return ForegroundRuleDecision(
                targetAppActive = true,
                action = ForegroundAction.Reconnect(
                    reason = "检测到白名单应用进入前台：${context.appLabel ?: context.packageName}",
                ),
            )
        }
        if (!targetAppActive && context.settings.appExitDisconnectEnabled) {
            return ForegroundRuleDecision(
                targetAppActive = false,
                action = if (context.settings.appExitDelaySeconds > 0) {
                    ForegroundAction.ScheduleDisconnect(
                        reason = if (context.previousTargetAppActive == true) {
                            "白名单应用已离开前台，将在 ${context.settings.appExitDelaySeconds} 秒后断网"
                        } else {
                            "当前前台应用不在白名单内，将在 ${context.settings.appExitDelaySeconds} 秒后断网"
                        },
                        delaySeconds = context.settings.appExitDelaySeconds,
                    )
                } else {
                    ForegroundAction.Disconnect(
                        reason = if (context.previousTargetAppActive == true) {
                            "白名单应用已离开前台"
                        } else {
                            "当前前台应用不在白名单内"
                        },
                    )
                },
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = targetAppActive,
            action = if (targetAppActive) {
                ForegroundAction.CancelScheduledDisconnect(
                    reason = "白名单应用重新进入前台，已取消待执行的离开应用断网",
                )
            } else {
                ForegroundAction.None
            },
        )
    }

    fun shouldScheduleScreenOffDisconnect(settings: UserSettings): Boolean =
        settings.automationEnabled && settings.screenOffDisconnectEnabled

    fun shouldExecuteScreenOffDisconnect(
        settings: UserSettings,
        screenState: ScreenState,
    ): Boolean = settings.automationEnabled &&
        settings.screenOffDisconnectEnabled &&
        screenState == ScreenState.ScreenOff

    companion object {
        const val SCREEN_ON_POLL_INTERVAL_MILLIS = 1_500L
        const val SCREEN_OFF_POLL_INTERVAL_MILLIS = 8_000L
        const val SCREEN_OFF_IDLE_POLL_INTERVAL_MILLIS = 30_000L
    }
}

data class ForegroundRuleContext(
    val settings: UserSettings,
    val packageName: String?,
    val appLabel: String?,
    val whitelistPackages: Set<String>,
    val previousTargetAppActive: Boolean?,
)

data class ForegroundRuleDecision(
    val targetAppActive: Boolean,
    val action: ForegroundAction,
)

sealed interface ForegroundAction {
    data object None : ForegroundAction

    data class Reconnect(
        val reason: String,
    ) : ForegroundAction

    data class Disconnect(
        val reason: String,
    ) : ForegroundAction

    data class ScheduleDisconnect(
        val reason: String,
        val delaySeconds: Int,
    ) : ForegroundAction

    data class CancelScheduledDisconnect(
        val reason: String,
    ) : ForegroundAction
}
