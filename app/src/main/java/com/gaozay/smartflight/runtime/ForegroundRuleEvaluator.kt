package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import javax.inject.Inject

class ForegroundRuleEvaluator @Inject constructor() {
    fun evaluate(context: ForegroundRuleContext): ForegroundRuleDecision {
        val targetAppActive = context.isTargetAppActive()
        if (context.previousTargetAppActive == null) {
            return none(
                targetAppActive = targetAppActive,
                reason = "首次同步前台应用状态，不执行自动动作",
                matchedRules = listOf("InitialForegroundSync"),
                shouldLog = false,
            )
        }
        if (!context.settings.isAutomationEffectivelyEnabled()) {
            return none(
                targetAppActive = targetAppActive,
                actionReason = "自动化已关闭，跳过前台应用规则",
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
        if (context.previousTargetAppActive == true &&
            !targetAppActive &&
            context.settings.appExitDisconnectEnabled
        ) {
            return buildAppExitDisconnectDecision(
                context = context,
                reason = "联网应用已离开前台",
                alreadyDisconnectedReason = "当前已断网，跳过离开目标应用后的重复断网",
            )
        }
        if (context.isInBlacklist) {
            return evaluateBlacklistApp(context)
        }

        val reconnectDecision = evaluateTargetAppReconnect(context, targetAppActive)
        if (reconnectDecision != null) {
            return reconnectDecision
        }

        if (context.previousTargetAppActive == targetAppActive) {
            return none(
                targetAppActive = targetAppActive,
                reason = "前台应用目标状态未变化",
                matchedRules = emptyList(),
                shouldLog = false,
            )
        }
        return if (targetAppActive) {
            ForegroundRuleDecision(
                targetAppActive = true,
                action = ForegroundAction.CancelScheduledDisconnect(
                    reason = "联网应用重新进入前台，已取消待执行的离开应用断网",
                ),
                reason = "联网应用重新进入前台，已取消待执行的离开应用断网",
                matchedRules = listOf("CancelAppExitDisconnect"),
                shouldLog = true,
            )
        } else {
            none(
                targetAppActive = false,
                reason = "没有命中需要执行的前台应用规则",
                matchedRules = emptyList(),
                shouldLog = false,
            )
        }
    }

    private fun evaluateBlacklistApp(context: ForegroundRuleContext): ForegroundRuleDecision {
        if (context.isCurrentlyDisconnected == true) {
            return none(
                targetAppActive = false,
                reason = "当前已断网，跳过黑名单应用的重复断网",
                matchedRules = listOf("Blacklist", "AlreadyDisconnected"),
                shouldLog = false,
            )
        }
        if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
            return none(
                targetAppActive = false,
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

    private fun evaluateTargetAppReconnect(
        context: ForegroundRuleContext,
        targetAppActive: Boolean,
    ): ForegroundRuleDecision? {
        val shouldReconnectForTargetApp = targetAppActive &&
            context.settings.reconnectOnTargetAppLaunch &&
            (
                context.previousTargetAppActive != targetAppActive ||
                    context.allowReconnectWhenTargetAppAlreadyActive ||
                    context.isCurrentlyDisconnected == true
            )
        if (!shouldReconnectForTargetApp) {
            return null
        }
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

    private fun buildAppExitDisconnectDecision(
        context: ForegroundRuleContext,
        reason: String,
        alreadyDisconnectedReason: String,
    ): ForegroundRuleDecision {
        if (context.isCurrentlyDisconnected == true) {
            return none(
                targetAppActive = false,
                reason = alreadyDisconnectedReason,
                matchedRules = listOf("AppExitDisconnect", "AlreadyDisconnected"),
                shouldLog = false,
            )
        }
        if (context.isAppExitDisconnectScheduled) {
            return none(
                targetAppActive = false,
                reason = "离开目标应用延时断网已在计时中",
                matchedRules = listOf("AppExitDisconnect", "AlreadyScheduled"),
                shouldLog = false,
            )
        }
        if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
            return none(
                targetAppActive = false,
                reason = "当前连接 Wi‑Fi，已跳过离开目标应用断网",
                matchedRules = listOf("AppExitDisconnect", "SkipDisconnectOnWifi"),
                shouldLog = true,
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = false,
            action = if (context.settings.appExitDelaySeconds > 0) {
                ForegroundAction.ScheduleDisconnect(
                    reason = "$reason，将在 ${context.settings.appExitDelaySeconds} 秒后断网",
                    delaySeconds = context.settings.appExitDelaySeconds,
                )
            } else {
                ForegroundAction.Disconnect(reason = reason)
            },
            reason = reason,
            matchedRules = listOf("AppExitDisconnect"),
            shouldLog = true,
        )
    }

    private fun none(
        targetAppActive: Boolean,
        reason: String,
        matchedRules: List<String>,
        shouldLog: Boolean,
        actionReason: String = reason,
    ): ForegroundRuleDecision = ForegroundRuleDecision(
        targetAppActive = targetAppActive,
        action = ForegroundAction.None(actionReason),
        reason = reason,
        matchedRules = matchedRules,
        shouldLog = shouldLog,
    )
}
