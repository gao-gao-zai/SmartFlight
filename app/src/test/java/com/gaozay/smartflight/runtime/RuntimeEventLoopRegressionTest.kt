package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.activities.ActivityRuntimeRule
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeEventLoopRegressionTest : LocalizedStringsTest() {
    private fun settings() = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true,
        appExitDelaySeconds = 10, screenOffDisconnectEnabled = true, screenOffDelaySeconds = 10,
        reconnectOnTargetAppLaunch = false, foregroundMonitorMode = ForegroundMonitorMode.Accessibility)

    @Test fun longStayThenRealExitDisconnectsOnceAfterFullDelay() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        advanceTimeBy(7 * 24 * 60 * 60 * 1000L); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.event("offline", 2); runCurrent()
        assertTrue(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        advanceTimeBy(9_999); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
    }

    @Test fun dueQueuedBeforeRapidOnlineReturnAndExitCannotUseOldDeadline() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        // Hold the actual coordinator inside an environment refresh while its timer
        // expires; the source has already advanced before queued transitions run.
        f.environment.blockRefresh = CompletableDeferred()
        f.runtime.onNetworkChanged(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        f.event("online", 3)
        f.event("offline", 4)
        f.environment.blockRefresh!!.complete(Unit); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        advanceTimeBy(9_999); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    @Test fun unknownAtExpiryCancelsAndRecoveryOfflineStartsFreshCountdown() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        f.source.current = null
        advanceTimeBy(10_000); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertNull(f.status.currentSnapshot.currentForegroundPackageName)
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        f.event("offline", 3); runCurrent()
        assertTrue(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    @Test fun sourceLossAndRecoveryOnlineDoesNotDisconnect() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        f.source.accessibilityConnected = false
        f.source.current = null
        f.runtime.onForegroundEventSourceChanged(); runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.source.accessibilityConnected = true
        f.event("online", 3); runCurrent()
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
    }

    @Test fun repeatedStartPreservesTimerAndStopStartInvalidatesOldQueuedWork() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOff); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        f.runtime.start(ScreenState.ScreenOff); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
        f.access.disconnectedRequests.clear()
        f.runtime.requestStop(ScreenState.ScreenOff)
        f.runtime.start(ScreenState.ScreenOn)
        runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertTrue(f.status.currentSnapshot.isForegroundServiceRunning)
        f.runtime.stop(ScreenState.ScreenOn)
        assertFalse(f.status.currentSnapshot.isForegroundServiceRunning)
    }

    @Test fun screenWakeEnableAndDelayEditRebuildScreenOffTask() = runTest {
        val f = fixture(settings().copy(automationEnabled = false))
        f.runtime.start(ScreenState.ScreenOff); runCurrent()
        assertFalse(f.status.currentSnapshot.isScreenOffDisconnectScheduled)
        f.settings.setAutomationEnabled(true); runCurrent()
        assertTrue(f.status.currentSnapshot.isScreenOffDisconnectScheduled)
        advanceTimeBy(5_000); runCurrent()
        f.settings.updateSettings { it.copy(screenOffDelaySeconds = 20) }; runCurrent()
        advanceTimeBy(19_999); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.runtime.onScreenOn(); runCurrent()
        advanceTimeBy(1); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.runtime.onScreenOff(); runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    @Test fun exitDelayEditRestartsFullCountdownAndDisablingCancelsIt() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        f.settings.updateSettings { it.copy(appExitDelaySeconds = 20) }; runCurrent()
        advanceTimeBy(19_999); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.settings.updateSettings { it.copy(appExitDisconnectEnabled = false) }; runCurrent()
        advanceTimeBy(1); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
    }

    @Test fun activityFollowAppAndRuleEditsAreEvaluatedWithoutNewResume() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.rules.value = listOf(ActivityRuntimeRule("online", "online.Main", "Offline", true, true)); runCurrent()
        assertTrue(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        f.rules.value = listOf(ActivityRuntimeRule("online", "online.Main", "FollowApp", true, true)); runCurrent()
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        f.apps.upsertApps(listOf(app("online", false))); runCurrent()
        assertTrue(f.status.currentSnapshot.isAppExitDisconnectScheduled)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    @Test fun executorLossAtDuePreventsWriteAndLaterConfirmationCanRetryExit() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        f.access.setAccess(AccessGateState())
        advanceTimeBy(10_000); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        f.access.setAccess(availableAccessGateState())
        f.event("offline", 3); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    @Test fun stoppedQueuedForegroundEventCannotRestartAutomation() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2)
        f.runtime.requestStop(ScreenState.ScreenOn)
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertFalse(f.status.currentSnapshot.isForegroundServiceRunning)
    }

    @Test fun wakeQueuedBehindScreenOffDueCancelsBeforeAnyWrite() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOff); runCurrent()
        f.environment.blockRefresh = CompletableDeferred()
        f.runtime.onNetworkChanged(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        f.runtime.onScreenOn()
        f.environment.blockRefresh!!.complete(Unit); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertFalse(f.status.currentSnapshot.isScreenOffDisconnectScheduled)
    }

    @Test fun disablingQueuedBehindDueCancelsBeforeAnyWrite() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.event("offline", 2); runCurrent()
        f.environment.blockRefresh = CompletableDeferred()
        f.runtime.onNetworkChanged(); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        f.settings.setAutomationEnabled(false); runCurrent()
        f.environment.blockRefresh!!.complete(Unit); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        assertFalse(f.status.currentSnapshot.isAppExitDisconnectScheduled)
    }

    @Test fun callbackPreservesPreviousSnapshotForExternalChangeGuard() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.environment.onRefresh = { f.status.updateSnapshot { it.copy(isAirplaneModeEnabled = true) } }
        f.environment.listener!!.invoke(); runCurrent()
        assertEquals(AutomationDisableMode.UntilAppSwitch, f.settings.currentSettings.temporaryDisableMode)
        assertTrue(f.access.disconnectedRequests.isEmpty())
    }

    @Test fun oldEnvironmentListenerCannotAffectRestartedRuntime() = runTest {
        val f = fixture()
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        val old = f.environment.listener!!
        f.runtime.requestStop(ScreenState.ScreenOn)
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        val refreshes = f.environment.refreshes
        old(); runCurrent()
        assertEquals(refreshes, f.environment.refreshes)
    }

    @Test fun screenOffRestartRebuildsFullDelayAndUntilScreenOffPauseClears() = runTest {
        val f = fixture(settings().copy(temporaryDisableMode = AutomationDisableMode.UntilScreenOff))
        f.runtime.start(ScreenState.ScreenOn); runCurrent()
        f.runtime.onScreenOff(); runCurrent()
        assertEquals(AutomationDisableMode.None, f.settings.currentSettings.temporaryDisableMode)
        advanceTimeBy(5_000); runCurrent()
        f.runtime.requestStop(ScreenState.ScreenOff)
        f.runtime.start(ScreenState.ScreenOff); runCurrent()
        advanceTimeBy(9_999); runCurrent()
        assertTrue(f.access.disconnectedRequests.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(true), f.access.disconnectedRequests)
    }

    private fun TestScope.fixture(initialSettings: UserSettings = settings()): Fixture {
        val settings = FakeSettingsRepository(initialSettings)
        val apps = FakeInstalledAppRepository(listOf(app("online", true)))
        val rules = MutableStateFlow(emptyList<ActivityRuntimeRule>())
        val access = FakeAccessRepository(availableAccessGateState())
        val source = ScriptSource()
        val status = FakeRuntimeStatusRepository(RuntimeSnapshot(isAirplaneModeEnabled = false))
        val reporter = RuntimeSnapshotReporter(status)
        val prompt = NoOpRuntimePromptNotifier()
        val expected = RuntimeExpectedNetworkChangeTracker()
        val executor = RuntimeNetworkChangeExecutor(access, reporter, prompt, expected)
        val engine = AutomationRuleEngine(ForegroundRuleEvaluator())
        val disconnect = DisconnectAutomationHandler(ScreenOffDisconnectHandler(access, engine, reporter, executor),
            AppExitDisconnectHandler(access, reporter, executor))
        val temporary = TemporaryDisableHandler(settings, reporter, prompt)
        val foreground = ForegroundAutomationHandler(access, source, engine, reporter, executor, disconnect, temporary)
        val environment = ScriptEnvironment()
        val runtime = AutomationRuntimeCoordinator(settings, apps, { rules }, access, engine, environment, source,
            reporter, foreground, disconnect, temporary, UnexpectedNetworkChangeGuard(settings, reporter, prompt, expected),
            backgroundScope, { testScheduler.currentTime })
        return Fixture(runtime, settings, apps, rules, access, source, status, environment)
    }

    private class ScriptSource : RuntimeForegroundSource {
        override var monitorMode = ForegroundMonitorMode.Accessibility
        override var confirmActivity = false
        override var accessibilityConnected = true
        var current: ForegroundAppInfo? = info("online", 1)
        override fun detect() = current
        override fun detectForEvent(event: ForegroundAppInfo) = event
    }
    private class ScriptEnvironment : RuntimeEnvironmentSource {
        var blockRefresh: CompletableDeferred<Unit>? = null
        var listener: (() -> Unit)? = null
        var onRefresh: (suspend () -> Unit)? = null
        var refreshes = 0
        override suspend fun refreshSnapshot() {
            refreshes++
            blockRefresh?.await()
            onRefresh?.invoke()
        }
        override fun register(onNetworkChanged: () -> Unit) { listener = onNetworkChanged }
        override fun unregister() { listener = null }
    }
    private data class Fixture(val runtime: AutomationRuntimeCoordinator, val settings: FakeSettingsRepository,
        val apps: FakeInstalledAppRepository, val rules: MutableStateFlow<List<ActivityRuntimeRule>>,
        val access: FakeAccessRepository, val source: ScriptSource, val status: FakeRuntimeStatusRepository,
        val environment: ScriptEnvironment) {
        fun event(pkg: String, at: Long) {
            val event = info(pkg, at)
            source.current = event
            runtime.onForegroundAppChanged(event)
        }
    }
    companion object {
        private fun info(pkg: String, at: Long) = ForegroundAppInfo(pkg, pkg, at, "$pkg.Main",
            ForegroundInfoSource.Accessibility, true, at)
        private fun app(pkg: String, online: Boolean) = InstalledAppEntity(pkg, pkg, null, false, true, true,
            false, online, online, false, 0)
    }
}
