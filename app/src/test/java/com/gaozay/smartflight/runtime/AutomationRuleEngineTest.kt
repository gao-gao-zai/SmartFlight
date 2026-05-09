package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.AppListStatus
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuleEngineTest {
    private val engine = AutomationRuleEngine()

    @Test
    fun automationDisabledReturnsNoAction() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = false),
                appStatus = AppListStatus.Whitelist,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertEquals("自动化已关闭", decision.reason)
        assertFalse(decision.shouldLog)
    }

    @Test
    fun executorUnavailablePausesAutomation() {
        val decision = engine.evaluateForegroundChange(
            context(
                appStatus = AppListStatus.Whitelist,
                executorAvailable = false,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.PauseAutomation)
        assertEquals(listOf("ExecutorUnavailable"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun whitelistAppReconnectsWhenEnteringForeground() {
        val decision = engine.evaluateForegroundChange(
            context(
                appStatus = AppListStatus.Whitelist,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("Whitelist"), decision.matchedRules)
        assertTrue(decision.targetAppActive)
    }

    @Test
    fun candidateAppReconnectsWhenWhitelistOnlyIsDisabled() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, whitelistOnly = false),
                appStatus = AppListStatus.Candidate,
                isCandidate = true,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("Candidate"), decision.matchedRules)
        assertTrue(decision.targetAppActive)
    }

    @Test
    fun candidateAppDoesNotTriggerWhenWhitelistOnlyIsEnabled() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, whitelistOnly = true, appExitDisconnectEnabled = false),
                appStatus = AppListStatus.Candidate,
                isCandidate = true,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertFalse(decision.targetAppActive)
        assertFalse(decision.shouldLog)
    }

    @Test
    fun ignoredAppDoesNotTriggerReconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = false),
                appStatus = AppListStatus.Ignored,
                isCandidate = true,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertFalse(decision.targetAppActive)
    }

    @Test
    fun blacklistAppDisconnects() {
        val decision = engine.evaluateForegroundChange(
            context(
                appStatus = AppListStatus.Blacklist,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Disconnect)
        assertEquals(listOf("Blacklist"), decision.matchedRules)
        assertFalse(decision.targetAppActive)
    }

    @Test
    fun wifiSkipsTargetReconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, skipReconnectOnWifi = true),
                appStatus = AppListStatus.Whitelist,
                isWifiConnected = true,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.CancelScheduledDisconnect)
        assertEquals(listOf("Whitelist", "SkipReconnectOnWifi"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun wifiSkipsAppExitDisconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, skipDisconnectOnWifi = true),
                appStatus = null,
                isWifiConnected = true,
                previousTargetAppActive = true,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertEquals(listOf("AppExitDisconnect", "SkipDisconnectOnWifi"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun leavingTargetAppSchedulesDisconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true, appExitDelaySeconds = 45),
                appStatus = null,
                previousTargetAppActive = true,
            ),
        )

        val action = decision.action
        assertTrue(action is ForegroundAction.ScheduleDisconnect)
        assertEquals(45, (action as ForegroundAction.ScheduleDisconnect).delaySeconds)
        assertEquals(listOf("AppExitDisconnect"), decision.matchedRules)
    }

    @Test
    fun screenOffDisconnectRequiresEnabledAutomationExecutorAndNoWifiSkip() {
        assertTrue(
            engine.shouldExecuteScreenOffDisconnect(
                settings = UserSettings(automationEnabled = true, screenOffDisconnectEnabled = true),
                screenState = ScreenState.ScreenOff,
                isWifiConnected = false,
                executorAvailable = true,
            ),
        )
        assertFalse(
            engine.shouldExecuteScreenOffDisconnect(
                settings = UserSettings(automationEnabled = true, screenOffDisconnectEnabled = true, skipDisconnectOnWifi = true),
                screenState = ScreenState.ScreenOff,
                isWifiConnected = true,
                executorAvailable = true,
            ),
        )
        assertFalse(
            engine.shouldExecuteScreenOffDisconnect(
                settings = UserSettings(automationEnabled = true, screenOffDisconnectEnabled = true),
                screenState = ScreenState.ScreenOff,
                isWifiConnected = false,
                executorAvailable = false,
            ),
        )
    }

    private fun context(
        settings: UserSettings = UserSettings(automationEnabled = true),
        packageName: String? = "com.example.app",
        appLabel: String? = "Example",
        appStatus: AppListStatus? = AppListStatus.Whitelist,
        isCandidate: Boolean = appStatus == AppListStatus.Candidate,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
        previousTargetAppActive: Boolean? = false,
    ): ForegroundRuleContext = ForegroundRuleContext(
        settings = settings,
        packageName = packageName,
        appLabel = appLabel,
        appStatus = appStatus,
        isCandidate = isCandidate,
        isWifiConnected = isWifiConnected,
        executorAvailable = executorAvailable,
        previousTargetAppActive = previousTargetAppActive,
    )
}
