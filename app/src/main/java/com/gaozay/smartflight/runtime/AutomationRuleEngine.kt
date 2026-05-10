package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
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
        val targetAppActive = context.isTargetAppActive()
        if (context.previousTargetAppActive == null) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.None("首次同步前台应用状态，不执行自动动作"),
                reason = "首次同步前台应用状态，不执行自动动作",
                matchedRules = listOf("InitialForegroundSync"),
                shouldLog = false,
            )
        }
        if (!context.settings.automationEnabled) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.None("自动化已关闭，跳过前台应用规则"),
                reason = "自动化已关闭",
                matchedRules = listOf("AutomationDisabled"),
                shouldLog = false,
            )
        }
        if (!context.executorAvailable) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.PauseAutomation("执行器不可用，自动化已暂停"),
                reason = "执行器不可用，自动化已暂停",
                matchedRules = listOf("ExecutorUnavailable"),
                shouldLog = true,
            )
        }
        if (context.isInBlacklist) {
            if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
                return ForegroundRuleDecision(
                    targetAppActive = false,
                    action = ForegroundAction.None("当前连接 Wi‑Fi，已跳过黑名单应用断网"),
                    reason = "当前连接 Wi‑Fi，已跳过黑名单应用断网",
                    matchedRules = listOf("Blacklist", "SkipDisconnectOnWifi"),
                    shouldLog = true,
                )
            }
            return ForegroundRuleDecision(
                targetAppActive = false,
                action = ForegroundAction.Disconnect(
                    reason = "黑名单应用正在前台：${context.displayName()}",
                ),
                reason = "黑名单应用正在前台：${context.displayName()}",
                matchedRules = listOf("Blacklist"),
                shouldLog = true,
            )
        }
        if (context.previousTargetAppActive == targetAppActive) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.None("前台应用目标状态未变化"),
                reason = "前台应用目标状态未变化",
                matchedRules = emptyList(),
                shouldLog = false,
            )
        }
        if (targetAppActive && context.settings.reconnectOnTargetAppLaunch) {
            val targetRule = when (context.onlineSource) {
                AppOnlineSourceTag.Manual -> "ManualOnline"
                AppOnlineSourceTag.Auto -> "AutoOnline"
                null -> "OnlineList"
            }
            if (context.isWifiConnected && context.settings.skipReconnectOnWifi) {
                return ForegroundRuleDecision(
                    targetAppActive = true,
                    action = ForegroundAction.CancelScheduledDisconnect(
                        reason = "当前连接 Wi‑Fi，已跳过目标应用恢复联网",
                    ),
                    reason = "当前连接 Wi‑Fi，已跳过目标应用恢复联网",
                    matchedRules = listOf(targetRule, "SkipReconnectOnWifi"),
                    shouldLog = true,
                )
            }
            return ForegroundRuleDecision(
                targetAppActive = true,
                action = ForegroundAction.Reconnect(
                    reason = "检测到联网应用进入前台：${context.displayName()}",
                ),
                reason = "检测到联网应用进入前台：${context.displayName()}",
                matchedRules = listOf(targetRule),
                shouldLog = true,
            )
        }
        if (!targetAppActive && context.settings.appExitDisconnectEnabled) {
            if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
                return ForegroundRuleDecision(
                    targetAppActive = false,
                    action = ForegroundAction.None("当前连接 Wi‑Fi，已跳过离开目标应用断网"),
                    reason = "当前连接 Wi‑Fi，已跳过离开目标应用断网",
                    matchedRules = listOf("AppExitDisconnect", "SkipDisconnectOnWifi"),
                    shouldLog = true,
                )
            }
            val reason = if (context.previousTargetAppActive == true) {
                "联网应用已离开前台"
            } else {
                "当前前台应用不是联网应用"
            }
            return ForegroundRuleDecision(
                targetAppActive = false,
                action = if (context.settings.appExitDelaySeconds > 0) {
                    ForegroundAction.ScheduleDisconnect(
                        reason = "$reason，将在 ${context.settings.appExitDelaySeconds} 秒后断网",
                        delaySeconds = context.settings.appExitDelaySeconds,
                    )
                } else {
                    ForegroundAction.Disconnect(
                        reason = reason,
                    )
                },
                reason = reason,
                matchedRules = listOf("AppExitDisconnect"),
                shouldLog = true,
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = targetAppActive,
            action = if (targetAppActive) {
                ForegroundAction.CancelScheduledDisconnect(
                    reason = "联网应用重新进入前台，已取消待执行的离开应用断网",
                )
            } else {
                ForegroundAction.None("没有命中需要执行的前台应用规则")
            },
            reason = if (targetAppActive) {
                "联网应用重新进入前台，已取消待执行的离开应用断网"
            } else {
                "没有命中需要执行的前台应用规则"
            },
            matchedRules = if (targetAppActive) listOf("CancelAppExitDisconnect") else emptyList(),
            shouldLog = targetAppActive,
        )
    }

    fun shouldScheduleScreenOffDisconnect(
        settings: UserSettings,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
    ): Boolean =
        settings.automationEnabled &&
            executorAvailable &&
            settings.screenOffDisconnectEnabled &&
            !(isWifiConnected && settings.skipDisconnectOnWifi)

    fun shouldExecuteScreenOffDisconnect(
        settings: UserSettings,
        screenState: ScreenState,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
    ): Boolean = settings.automationEnabled &&
        executorAvailable &&
        settings.screenOffDisconnectEnabled &&
        screenState == ScreenState.ScreenOff &&
        !(isWifiConnected && settings.skipDisconnectOnWifi)

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
    val isInOnlineList: Boolean,
    val isInBlacklist: Boolean,
    val onlineSource: AppOnlineSourceTag?,
    val isWifiConnected: Boolean,
    val executorAvailable: Boolean,
    val previousTargetAppActive: Boolean?,
) {
    fun displayName(): String = appLabel ?: packageName ?: "未知应用"

    fun isTargetAppActive(): Boolean =
        packageName != null && !isInBlacklist && isInOnlineList
}

data class ForegroundRuleDecision(
    val targetAppActive: Boolean,
    val action: ForegroundAction,
    val reason: String,
    val matchedRules: List<String>,
    val shouldLog: Boolean,
)

sealed interface ForegroundAction {
    data class None(
        val reason: String = "",
    ) : ForegroundAction

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

    data class PauseAutomation(
        val reason: String,
    ) : ForegroundAction
}
