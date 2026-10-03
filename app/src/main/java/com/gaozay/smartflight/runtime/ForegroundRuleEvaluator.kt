package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import javax.inject.Inject

class ForegroundRuleEvaluator @Inject constructor() {
    fun evaluate(context: ForegroundRuleContext): ForegroundRuleDecision {
        val targetAppActive = context.isTargetAppActive()
        if (context.previousTargetAppActive == null) {
            return none(
                targetAppActive = targetAppActive,
                reason = AppStrings.get(R.string.initial_foreground_app_synchronization_no_automatic_action_taken),
                matchedRules = listOf("InitialForegroundSync"),
                shouldLog = false,
            )
        }
        if (!context.settings.isAutomationEffectivelyEnabled()) {
            return none(
                targetAppActive = targetAppActive,
                actionReason = AppStrings.get(R.string.automation_disabled_foreground_app_rules_skipped),
                reason = AppStrings.get(R.string.automation_disabled),
                matchedRules = listOf("AutomationDisabled"),
                shouldLog = false,
            )
        }
        if (!context.executorAvailable) {
            return ForegroundRuleDecision(
                targetAppActive = targetAppActive,
                action = ForegroundAction.PauseAutomation(AppStrings.get(R.string.executor_unavailable_automation_paused)),
                reason = AppStrings.get(R.string.executor_unavailable_automation_paused),
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
                reason = AppStrings.get(R.string.an_online_app_left_the_foreground_foreground_rule_evaluator),
                alreadyDisconnectedReason = AppStrings.get(R.string.runtime_app_exit_already_offline),
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
                reason = AppStrings.get(R.string.foreground_app_target_status_unchanged),
                matchedRules = emptyList(),
                shouldLog = false,
            )
        }
        return if (targetAppActive) {
            ForegroundRuleDecision(
                targetAppActive = true,
                action = ForegroundAction.CancelScheduledDisconnect(
                    reason = AppStrings.get(R.string.runtime_app_return_disconnect_canceled),
                ),
                reason = AppStrings.get(R.string.runtime_app_return_disconnect_canceled),
                matchedRules = listOf("CancelAppExitDisconnect"),
                shouldLog = true,
            )
        } else {
            none(
                targetAppActive = false,
                reason = AppStrings.get(R.string.no_foreground_app_rule_required_an_action),
                matchedRules = emptyList(),
                shouldLog = false,
            )
        }
    }

    private fun evaluateBlacklistApp(context: ForegroundRuleContext): ForegroundRuleDecision {
        if (context.isCurrentlyDisconnected == true) {
            return none(
                targetAppActive = false,
                reason = AppStrings.get(R.string.already_offline_duplicate_disconnect_for_the_blocklisted_app_skipped),
                matchedRules = listOf("Blacklist", "AlreadyDisconnected"),
                shouldLog = false,
            )
        }
        if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
            return none(
                targetAppActive = false,
                reason = AppStrings.get(R.string.connected_to_wi_fi_disconnect_for_the_blocklisted_app_skipped),
                matchedRules = listOf("Blacklist", "SkipDisconnectOnWifi"),
                shouldLog = true,
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = false,
            action = ForegroundAction.Disconnect(
                reason = AppStrings.get(R.string.blocklisted_app_in_the_foreground, context.displayName()),
            ),
            reason = AppStrings.get(R.string.blocklisted_app_in_the_foreground, context.displayName()),
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
                    reason = AppStrings.get(R.string.connected_to_wi_fi_reconnect_for_the_target_app_skipped),
                ),
                reason = AppStrings.get(R.string.connected_to_wi_fi_reconnect_for_the_target_app_skipped),
                matchedRules = listOf(targetRule, "SkipReconnectOnWifi"),
                shouldLog = true,
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = true,
            action = ForegroundAction.Reconnect(
                reason = AppStrings.get(R.string.online_app_entered_the_foreground, context.displayName()),
            ),
            reason = AppStrings.get(R.string.online_app_entered_the_foreground, context.displayName()),
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
                reason = AppStrings.get(R.string.app_exit_disconnect_countdown_is_already_running),
                matchedRules = listOf("AppExitDisconnect", "AlreadyScheduled"),
                shouldLog = false,
            )
        }
        if (context.isWifiConnected && context.settings.skipDisconnectOnWifi) {
            return none(
                targetAppActive = false,
                reason = AppStrings.get(R.string.connected_to_wi_fi_app_exit_disconnect_skipped),
                matchedRules = listOf("AppExitDisconnect", "SkipDisconnectOnWifi"),
                shouldLog = true,
            )
        }
        return ForegroundRuleDecision(
            targetAppActive = false,
            action = if (context.settings.appExitDelaySeconds > 0) {
                ForegroundAction.ScheduleDisconnect(
                    reason = AppStrings.quantity(R.plurals.disconnecting_in_seconds, (context.settings.appExitDelaySeconds).toInt(), reason, context.settings.appExitDelaySeconds),
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
