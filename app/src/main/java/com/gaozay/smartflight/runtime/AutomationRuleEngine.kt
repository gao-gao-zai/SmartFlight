package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRuleEngine @Inject constructor(
    private val foregroundRuleEvaluator: ForegroundRuleEvaluator,
) {
    fun shouldMonitorForeground(
        settings: UserSettings,
        screenState: ScreenState,
    ): Boolean = settings.monitorForegroundWhenScreenOff || screenState != ScreenState.ScreenOff

    fun evaluateForegroundChange(context: ForegroundRuleContext): ForegroundRuleDecision =
        foregroundRuleEvaluator.evaluate(context)

    fun shouldScheduleScreenOffDisconnect(
        settings: UserSettings,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
    ): Boolean =
        settings.isAutomationEffectivelyEnabled() &&
            executorAvailable &&
            settings.screenOffDisconnectEnabled &&
            !(isWifiConnected && settings.skipDisconnectOnWifi)

    fun shouldExecuteScreenOffDisconnect(
        settings: UserSettings,
        screenState: ScreenState,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
    ): Boolean = settings.isAutomationEffectivelyEnabled() &&
        executorAvailable &&
        settings.screenOffDisconnectEnabled &&
        screenState == ScreenState.ScreenOff &&
        !(isWifiConnected && settings.skipDisconnectOnWifi)
}
