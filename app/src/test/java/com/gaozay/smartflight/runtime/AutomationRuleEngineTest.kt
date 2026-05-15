package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
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
                isInOnlineList = true,
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
                isInOnlineList = true,
                executorAvailable = false,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.PauseAutomation)
        assertEquals(listOf("ExecutorUnavailable"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun manualOnlineAppReconnectsWhenEnteringForeground() {
        val decision = engine.evaluateForegroundChange(
            context(
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Manual,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("ManualOnline"), decision.matchedRules)
        assertTrue(decision.targetAppActive)
    }

    @Test
    fun autoOnlineAppReconnectsWhenEnteringForeground() {
        val decision = engine.evaluateForegroundChange(
            context(
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Auto,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("AutoOnline"), decision.matchedRules)
        assertTrue(decision.targetAppActive)
    }

    @Test
    fun screenWakeCanReconnectWhileTargetAppStaysForeground() {
        val decision = engine.evaluateForegroundChange(
            context(
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Manual,
                previousTargetAppActive = true,
                allowReconnectWhenTargetAppAlreadyActive = true,
                isCurrentlyDisconnected = true,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("ManualOnline"), decision.matchedRules)
    }

    @Test
    fun disconnectedTargetAppReconnectsWithoutWakeOverride() {
        val decision = engine.evaluateForegroundChange(
            context(
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Manual,
                previousTargetAppActive = true,
                isCurrentlyDisconnected = true,
            ),
        )

        assertTrue(decision.action is ForegroundAction.Reconnect)
        assertEquals(listOf("ManualOnline"), decision.matchedRules)
    }

    @Test
    fun unchangedConnectedTargetAppDoesNotReconnectWithoutWakeOverride() {
        val decision = engine.evaluateForegroundChange(
            context(
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Manual,
                previousTargetAppActive = true,
                isCurrentlyDisconnected = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertEquals("前台应用目标状态未变化", decision.reason)
    }

    @Test
    fun offlineAppDoesNotTriggerReconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = false),
                isInOnlineList = false,
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
                isInOnlineList = false,
                isInBlacklist = true,
                onlineSource = AppOnlineSourceTag.Manual,
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
                isInOnlineList = true,
                onlineSource = AppOnlineSourceTag.Manual,
                isWifiConnected = true,
                previousTargetAppActive = false,
            ),
        )

        assertTrue(decision.action is ForegroundAction.CancelScheduledDisconnect)
        assertEquals(listOf("ManualOnline", "SkipReconnectOnWifi"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun wifiSkipsAppExitDisconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, skipDisconnectOnWifi = true),
                packageName = "com.example.other",
                isInOnlineList = false,
                isWifiConnected = true,
                previousTargetAppActive = true,
            ),
        )

        assertTrue(decision.action is ForegroundAction.None)
        assertEquals(listOf("AppExitDisconnect", "SkipDisconnectOnWifi"), decision.matchedRules)
        assertTrue(decision.shouldLog)
    }

    @Test
    fun leavingOnlineAppSchedulesDisconnect() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true, appExitDelaySeconds = 45),
                packageName = "com.example.other",
                isInOnlineList = false,
                previousTargetAppActive = true,
            ),
        )

        val action = decision.action
        assertTrue(action is ForegroundAction.ScheduleDisconnect)
        assertEquals(45, (action as ForegroundAction.ScheduleDisconnect).delaySeconds)
        assertEquals(listOf("AppExitDisconnect"), decision.matchedRules)
    }

    @Test
    fun leavingOnlineAppForBlacklistStillSchedulesDisconnectFirst() {
        val decision = engine.evaluateForegroundChange(
            context(
                settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true, appExitDelaySeconds = 45),
                packageName = "com.example.black",
                isInOnlineList = false,
                isInBlacklist = true,
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
        isInOnlineList: Boolean = true,
        isInBlacklist: Boolean = false,
        onlineSource: AppOnlineSourceTag? = AppOnlineSourceTag.Manual,
        isWifiConnected: Boolean = false,
        executorAvailable: Boolean = true,
        previousTargetAppActive: Boolean? = false,
        isCurrentlyDisconnected: Boolean? = null,
        allowReconnectWhenTargetAppAlreadyActive: Boolean = false,
    ): ForegroundRuleContext = ForegroundRuleContext(
        settings = settings,
        packageName = packageName,
        appLabel = appLabel,
        isInOnlineList = isInOnlineList,
        isInBlacklist = isInBlacklist,
        onlineSource = onlineSource,
        isWifiConnected = isWifiConnected,
        executorAvailable = executorAvailable,
        previousTargetAppActive = previousTargetAppActive,
        isCurrentlyDisconnected = isCurrentlyDisconnected,
        allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
    )
}
