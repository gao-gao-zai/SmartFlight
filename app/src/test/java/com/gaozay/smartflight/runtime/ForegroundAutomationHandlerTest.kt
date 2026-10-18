package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.activities.ActivityRuntimeRule
import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAutomationHandlerTest : LocalizedStringsTest() {
    @Test
    fun unknownForegroundPreservesOnlineHistoryAndConfirmedExitStillGetsFullDelay() = runTest {
        val fixture = fixture(null, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true, appExitDelaySeconds = 60)
        var state = RuntimeState(settings = settings, lastTargetAppActive = true,
            lastKnownForegroundApp = ForegroundAppInfo("online.app", "Online", 1000))
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(true, state.lastTargetAppActive)
        assertNull(state.lastKnownForegroundApp)
        assertTrue(!fixture.scheduler.isAppExitDisconnectActive)
        fixture.foregroundAppSource.app = ForegroundAppInfo("other.app", "Other", 1000)
        val before = System.currentTimeMillis()
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(false, state.lastTargetAppActive)
        assertTrue(fixture.scheduler.isAppExitDisconnectActive)
        assertTrue(requireNotNull(fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis) >= before + 59_000)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        fixture.scheduler.cancelAll()
    }

    @Test
    fun unknownThenConfirmedOnlineReconnectsWhenRadioIsDisconnected() = runTest {
        val fixture = fixture(null, RuntimeSnapshot(isAirplaneModeEnabled = true))
        var state = RuntimeState(settings = UserSettings(automationEnabled = true), lastTargetAppActive = true,
            appRulesByPackageName = mapOf("online.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual)))
        state = fixture.handler.automationTick(state, fixture.scheduler)
        fixture.foregroundAppSource.app = ForegroundAppInfo("online.app", "Online", 1000)
        fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun coldUnknownDoesNotBecomeAnInitialOfflineSynchronization() = runTest {
        val fixture = fixture(null, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val state = fixture.handler.automationTick(RuntimeState(settings = UserSettings(automationEnabled = true)), fixture.scheduler)
        assertNull(state.lastTargetAppActive)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    @Test
    fun pendingExitIsPausedDuringUnknownAndRestartsAfterConfirmedOfflineRecovery() = runTest {
        val fixture = fixture(ForegroundAppInfo("other.app", "Other", 1000), RuntimeSnapshot(isAirplaneModeEnabled = false))
        var state = RuntimeState(settings = UserSettings(automationEnabled = true,
            appExitDisconnectEnabled = true, appExitDelaySeconds = 60), lastTargetAppActive = true)
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertTrue(fixture.scheduler.isAppExitDisconnectActive)
        fixture.foregroundAppSource.app = null
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(false, state.lastTargetAppActive)
        assertTrue(state.appExitAwaitingForegroundConfirmation)
        assertTrue(!fixture.scheduler.isAppExitDisconnectActive)
        fixture.foregroundAppSource.app = ForegroundAppInfo("other.app", "Other", 1000)
        val before = System.currentTimeMillis()
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertTrue(fixture.scheduler.isAppExitDisconnectActive)
        assertTrue(!state.appExitAwaitingForegroundConfirmation)
        assertTrue(requireNotNull(fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis) >= before + 59_000)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        fixture.scheduler.cancelAll()
    }

    @Test
    fun aNewClockEpochReplacesFutureDatedStateInStoreAndHandler() = runTest {
        val now = System.currentTimeMillis()
        val old = ForegroundAppInfo("old.app", "Old", now + 60_000,
            "old.app.Main", ForegroundInfoSource.Accessibility, true, now + 60_000)
        val current = ForegroundAppInfo("new.app", "New", now,
            "new.app.Main", ForegroundInfoSource.Accessibility, true, now)
        val store = ForegroundObservationStore()
        store.accept(old)
        assertEquals(current, store.accept(current))
        val fixture = fixture(current, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val state = fixture.handler.automationTick(RuntimeState(settings = UserSettings(automationEnabled = true),
            lastKnownForegroundApp = old), fixture.scheduler)
        assertEquals(current, state.lastKnownForegroundApp)
        assertEquals("new.app", fixture.runtimeStatusRepository.currentSnapshot.currentForegroundPackageName)
    }

    @Test
    fun firstSyncUpdatesTargetStateWithoutNetworkRequest() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = null,
        )

        val updated = fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(true, updated.lastTargetAppActive)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    @Test
    fun targetAppEnteringForegroundReconnects() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = true,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun wifiSkipReconnectLogsSkippedDecision() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(isWifiConnected = true, isAirplaneModeEnabled = true),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, skipReconnectOnWifi = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(ExecutionAction.CancelScheduledDisconnect, fixture.runtimeStatusRepository.currentSnapshot.lastAction)
        assertEquals(ExecutionResult.Skipped, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
    }

    @Test
    fun temporaryDisableUntilAppSwitchClearsAndContinuesEvaluating() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.next", "Next", 1_000L),
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = true,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(
                automationEnabled = true,
                temporaryDisableMode = AutomationDisableMode.UntilAppSwitch,
                temporaryDisableForegroundPackageName = "com.example.old",
            ),
            appRulesByPackageName = mapOf(
                "com.example.next" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        val updated = fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(AutomationDisableMode.None, updated.settings.temporaryDisableMode)
        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
        assertEquals(listOf("检测到应用切换，恢复自动化"), fixture.promptNotifier.automationRestoredPrompts)
    }

    @Test
    fun foregroundOverrideSkipsDetectorAndUsesProvidedApp() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.detector", "Detector", 1_000L),
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            appRulesByPackageName = mapOf(
                "com.example.override" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = null,
        )

        val updated = fixture.handler.automationTick(
            state = state,
            scheduler = fixture.scheduler,
            foregroundAppOverride = ForegroundAppInfo("com.example.override", "Override", 2_000L),
        )

        assertEquals(true, updated.lastTargetAppActive)
        assertEquals("com.example.override", fixture.runtimeStatusRepository.currentSnapshot.currentForegroundPackageName)
        assertEquals(0, fixture.foregroundAppSource.detectCalls)
    }

    @Test
    fun samePackageActivityChangeDoesNotClearUntilAppSwitchPause() = runTest {
        val now = System.currentTimeMillis()
        val info = ForegroundAppInfo("example.app", "Example", now, "example.app.Payment", ForegroundInfoSource.UsageStats, true, now)
        val fixture = fixture(info, RuntimeSnapshot(isAirplaneModeEnabled = true))
        val state = RuntimeState(settings = UserSettings(automationEnabled = true,
            temporaryDisableMode = AutomationDisableMode.UntilAppSwitch, temporaryDisableForegroundPackageName = "example.app"),
            lastTargetAppActive = false, activityRules = listOf(ActivityRuntimeRule("example.app", "example.app.Payment", "Online", true, true)))
        val updated = fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(AutomationDisableMode.UntilAppSwitch, updated.settings.temporaryDisableMode)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    @Test
    fun onlineActivityReconnectsEvenWhenItsAppDefaultIsBlacklisted() = runTest {
        val now = System.currentTimeMillis()
        val info = ForegroundAppInfo("example.app", "Example", now, "example.app.Payment", ForegroundInfoSource.UsageStats, true, now)
        val fixture = fixture(info, RuntimeSnapshot(isAirplaneModeEnabled = true))
        val state = RuntimeState(settings = UserSettings(automationEnabled = true), lastTargetAppActive = false,
            appRulesByPackageName = mapOf("example.app" to AppRuntimeRuleInfo(false, true, AppOnlineSourceTag.Manual)),
            activityRules = listOf(ActivityRuntimeRule("example.app", "example.app.Payment", "Online", true, true)))
        val updated = fixture.handler.automationTick(state, fixture.scheduler)
        assertEquals(true, updated.lastTargetAppActive)
        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
        assertEquals("Activity", fixture.runtimeStatusRepository.currentSnapshot.foregroundRuleLayer)
        assertEquals("example.app.Payment", fixture.runtimeStatusRepository.currentSnapshot.currentForegroundActivityName)
    }

    @Test
    fun repeatedOfflineEventsKeepExitCountdownAndReturningOnlineCancelsIt() = runTest {
        val now = System.currentTimeMillis()
        val info = ForegroundAppInfo("example.app", "Example", now, "example.app.Offline", ForegroundInfoSource.UsageStats, true, now)
        val fixture = fixture(info, RuntimeSnapshot(isAirplaneModeEnabled = false))
        var state = RuntimeState(settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true, appExitDelaySeconds = 60),
            lastTargetAppActive = true,
            appRulesByPackageName = mapOf("example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Auto)),
            activityRules = listOf(ActivityRuntimeRule("example.app", "example.app.Offline", "Offline", true, true)))
        state = fixture.handler.automationTick(state, fixture.scheduler)
        val deadline = fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis
        state = fixture.handler.automationTick(state, fixture.scheduler)
        assertTrue(fixture.scheduler.isAppExitDisconnectActive)
        assertEquals(deadline, fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        fixture.handler.automationTick(state, fixture.scheduler, foregroundAppOverride = info.copy(activityName = "example.app.Online", eventTimestampMillis = now + 1))
        assertTrue(!fixture.scheduler.isAppExitDisconnectActive)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        fixture.scheduler.cancelForegroundProbe()
    }

    @Test
    fun queuedOfflineEventCannotImmediatelyCutOffCurrentlyOnlineApp() = runTest {
        val online = ForegroundAppInfo("online", "Online", 3)
        val fixture = fixture(online, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val state = RuntimeState(settings = UserSettings(automationEnabled = true,
            appExitDisconnectEnabled = true, appExitDelaySeconds = 0, reconnectOnTargetAppLaunch = false),
            lastTargetAppActive = true,
            appRulesByPackageName = mapOf("online" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual)))
        val updated = fixture.handler.automationTick(state, fixture.scheduler,
            foregroundAppOverride = ForegroundAppInfo("offline", "Offline", 2))
        assertEquals(true, updated.lastTargetAppActive)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    private fun fixture(
        foregroundApp: ForegroundAppInfo?,
        snapshot: RuntimeSnapshot,
    ): Fixture {
        val runtimeStatusRepository = FakeRuntimeStatusRepository(snapshot)
        val reporter = RuntimeSnapshotReporter(runtimeStatusRepository)
        val accessRepository = FakeAccessRepository(availableAccessGateState())
        val scheduler = RuntimeTaskScheduler(
            scope = TestScope(StandardTestDispatcher()),
            send = {},
            reporter = reporter,
        )
        val promptNotifier = NoOpRuntimePromptNotifier()
        val networkExecutor = RuntimeNetworkChangeExecutor(
            accessRepository = accessRepository,
            reporter = reporter,
            promptNotifier = promptNotifier,
            expectedNetworkChangeTracker = RuntimeExpectedNetworkChangeTracker(),
        )
        val disconnectHandler = DisconnectAutomationHandler(
            screenOffDisconnectHandler = ScreenOffDisconnectHandler(
                accessRepository = accessRepository,
                automationRuleEngine = AutomationRuleEngine(ForegroundRuleEvaluator()),
                reporter = reporter,
                networkChangeExecutor = networkExecutor,
            ),
            appExitDisconnectHandler = AppExitDisconnectHandler(
                accessRepository = accessRepository,
                reporter = reporter,
                networkChangeExecutor = networkExecutor,
            ),
        )
        val settingsRepository = FakeSettingsRepository()
        val foregroundAppSource = FakeForegroundAppSource(foregroundApp)
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            accessRepository = accessRepository,
            scheduler = scheduler,
            handler = ForegroundAutomationHandler(
                accessRepository = accessRepository,
                foregroundAppSource = foregroundAppSource,
                automationRuleEngine = AutomationRuleEngine(ForegroundRuleEvaluator()),
                reporter = reporter,
                networkChangeExecutor = networkExecutor,
                disconnectAutomationHandler = disconnectHandler,
                temporaryDisableHandler = TemporaryDisableHandler(settingsRepository, reporter, promptNotifier),
            ),
            foregroundAppSource = foregroundAppSource,
            promptNotifier = promptNotifier,
        )
    }

    private data class Fixture(
        val runtimeStatusRepository: FakeRuntimeStatusRepository,
        val accessRepository: FakeAccessRepository,
        val scheduler: RuntimeTaskScheduler,
        val handler: ForegroundAutomationHandler,
        val foregroundAppSource: FakeForegroundAppSource,
        val promptNotifier: NoOpRuntimePromptNotifier,
    )
}
