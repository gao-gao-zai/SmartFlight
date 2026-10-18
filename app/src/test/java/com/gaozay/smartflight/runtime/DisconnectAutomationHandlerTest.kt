package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectAutomationHandlerTest : LocalizedStringsTest() {
    @Test
    fun screenOffSchedulesDisconnectWhenConditionsMatch() = runTest {
        val fixture = fixture(
            scope = this,
            snapshot = RuntimeSnapshot(isWifiConnected = false, isAirplaneModeEnabled = false),
        )
        val state = RuntimeState(
            settings = UserSettings(
                automationEnabled = true,
                screenOffDisconnectEnabled = true,
                screenOffDelaySeconds = 5,
            ),
            screenState = ScreenState.ScreenOff,
        )

        fixture.handler.scheduleScreenOffDisconnectIfNeeded(state, fixture.scheduler)

        assertTrue(fixture.scheduler.isScreenOffDisconnectActive)
        assertTrue(fixture.runtimeStatusRepository.currentSnapshot.isScreenOffDisconnectScheduled)
        assertEquals(ExecutionAction.ScheduleScreenOffDisconnect, fixture.runtimeStatusRepository.currentSnapshot.lastAction)
    }

    @Test
    fun screenOffScheduleSkipsWhenAlreadyDisconnected() = runTest {
        val fixture = fixture(
            scope = this,
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = true),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, screenOffDisconnectEnabled = true),
            screenState = ScreenState.ScreenOff,
        )

        fixture.handler.scheduleScreenOffDisconnectIfNeeded(state, fixture.scheduler)

        assertFalse(fixture.scheduler.isScreenOffDisconnectActive)
        assertFalse(fixture.runtimeStatusRepository.currentSnapshot.isScreenOffDisconnectScheduled)
    }

    @Test
    fun appExitDueExecutesDisconnectWhenStillEligible() = runTest {
        val fixture = fixture(
            scope = this,
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = false,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true),
            lastTargetAppActive = false,
            lastKnownForegroundApp = ForegroundAppInfo("offline.app", "Offline", 1_000L),
        )

        fixture.scheduler.scheduleAppExitDisconnect(0L)
        runCurrent()
        val due = fixture.events.single() as RuntimeEvent.AppExitDisconnectDue
        fixture.handler.handleAppExitDisconnectDue(state, fixture.scheduler, due)

        assertEquals(listOf(true), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun oldAppExitDueDoesNotExecuteOrEraseNewCountdown() = runTest {
        val fixture = fixture(this, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            lastTargetAppActive = false,
            lastKnownForegroundApp = ForegroundAppInfo("offline.app", "Offline", 1_000L),
        )
        fixture.scheduler.scheduleAppExitDisconnect(0L)
        runCurrent()
        val stale = fixture.events.single() as RuntimeEvent.AppExitDisconnectDue
        fixture.scheduler.cancelAppExitDisconnect()
        fixture.handler.scheduleAppExitDisconnect(state, fixture.scheduler, "new exit", 30)
        val deadline = fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis
        fixture.handler.handleAppExitDisconnectDue(state, fixture.scheduler, stale)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        assertTrue(fixture.scheduler.isAppExitDisconnectActive)
        assertEquals(deadline, fixture.runtimeStatusRepository.currentSnapshot.pendingAppExitDisconnectAtMillis)
        advanceTimeBy(30_000L)
        runCurrent()
        val current = fixture.events.last() as RuntimeEvent.AppExitDisconnectDue
        fixture.handler.handleAppExitDisconnectDue(state, fixture.scheduler, current)
        assertEquals(listOf(true), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun oldScreenOffDueDoesNotExecuteOrEraseNewCountdown() = runTest {
        val fixture = fixture(this, RuntimeSnapshot(isAirplaneModeEnabled = false))
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, screenOffDelaySeconds = 60),
            screenState = ScreenState.ScreenOff,
        )
        fixture.scheduler.scheduleScreenOffDisconnect(0L)
        runCurrent()
        val stale = fixture.events.single() as RuntimeEvent.ScreenOffDisconnectDue
        fixture.scheduler.cancelScreenOffDisconnect()
        fixture.handler.scheduleScreenOffDisconnectIfNeeded(state, fixture.scheduler)
        val deadline = fixture.runtimeStatusRepository.currentSnapshot.pendingScreenOffDisconnectAtMillis
        fixture.handler.handleScreenOffDisconnectDue(state, fixture.scheduler, stale)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
        assertTrue(fixture.scheduler.isScreenOffDisconnectActive)
        assertEquals(deadline, fixture.runtimeStatusRepository.currentSnapshot.pendingScreenOffDisconnectAtMillis)
        advanceTimeBy(60_000L)
        runCurrent()
        fixture.handler.handleScreenOffDisconnectDue(state, fixture.scheduler, fixture.events.last() as RuntimeEvent.ScreenOffDisconnectDue)
        assertEquals(listOf(true), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun appExitDueRequiresKnownForegroundEvenWithOfflineHistory() = runTest {
        val fixture = fixture(this, RuntimeSnapshot(isAirplaneModeEnabled = false))
        fixture.scheduler.scheduleAppExitDisconnect(0L)
        runCurrent()
        fixture.handler.handleAppExitDisconnectDue(
            RuntimeState(settings = UserSettings(automationEnabled = true), lastTargetAppActive = false),
            fixture.scheduler,
            fixture.events.single() as RuntimeEvent.AppExitDisconnectDue,
        )
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    @Test
    fun screenOnCannotScheduleScreenOffCountdown() = runTest {
        val fixture = fixture(this, RuntimeSnapshot(isAirplaneModeEnabled = false))
        fixture.handler.scheduleScreenOffDisconnectIfNeeded(
            RuntimeState(settings = UserSettings(automationEnabled = true), screenState = ScreenState.ScreenOn),
            fixture.scheduler,
        )
        assertFalse(fixture.scheduler.isScreenOffDisconnectActive)
    }

    private fun fixture(
        scope: TestScope,
        snapshot: RuntimeSnapshot,
    ): Fixture {
        val runtimeStatusRepository = FakeRuntimeStatusRepository(snapshot)
        val reporter = RuntimeSnapshotReporter(runtimeStatusRepository)
        val accessRepository = FakeAccessRepository(availableAccessGateState())
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = RuntimeTaskScheduler(
            scope = scope.backgroundScope,
            send = { events += it },
            reporter = reporter,
            monotonicTimeMillis = { scope.testScheduler.currentTime },
        )
        val networkExecutor = RuntimeNetworkChangeExecutor(
            accessRepository = accessRepository,
            reporter = reporter,
            promptNotifier = NoOpRuntimePromptNotifier(),
            expectedNetworkChangeTracker = RuntimeExpectedNetworkChangeTracker(),
        )
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            accessRepository = accessRepository,
            scheduler = scheduler,
            events = events,
            handler = DisconnectAutomationHandler(
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
            ),
        )
    }

    private data class Fixture(
        val runtimeStatusRepository: FakeRuntimeStatusRepository,
        val accessRepository: FakeAccessRepository,
        val scheduler: RuntimeTaskScheduler,
        val events: MutableList<RuntimeEvent>,
        val handler: DisconnectAutomationHandler,
    )
}
