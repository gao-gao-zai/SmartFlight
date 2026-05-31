package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectAutomationHandlerTest {
    @Test
    fun screenOffSchedulesDisconnectWhenConditionsMatch() = runTest {
        val fixture = fixture(
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
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = false,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, appExitDisconnectEnabled = true),
            lastTargetAppActive = false,
        )

        fixture.handler.handleAppExitDisconnectDue(state, fixture.scheduler)

        assertEquals(listOf(true), fixture.accessRepository.disconnectedRequests)
    }

    private fun fixture(
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
        val networkExecutor = RuntimeNetworkChangeExecutor(
            accessRepository = accessRepository,
            reporter = reporter,
            promptNotifier = NoOpRuntimePromptNotifier(),
        )
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            accessRepository = accessRepository,
            scheduler = scheduler,
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
        val handler: DisconnectAutomationHandler,
    )
}
