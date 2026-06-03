package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ScreenState
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeTaskSchedulerTest {
    @Test
    fun foregroundProbeIntervalKeepsOriginalCadence() {
        assertEquals(1_500L, RuntimeTaskScheduler.foregroundProbeIntervalMillis(ScreenState.ScreenOn))
        assertEquals(1_500L, RuntimeTaskScheduler.foregroundProbeIntervalMillis(ScreenState.Unlocked))
        assertEquals(8_000L, RuntimeTaskScheduler.foregroundProbeIntervalMillis(ScreenState.ScreenOff))
    }

    @Test
    fun immediateForegroundProbeSendsTickWithoutDelay() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)

        scheduler.scheduleForegroundProbe(
            state = RuntimeState(settings = com.gaozay.smartflight.settings.UserSettings(automationEnabled = true)),
            automationRuleEngine = AutomationRuleEngine(ForegroundRuleEvaluator()),
            immediate = true,
        )
        runCurrent()

        assertEquals(listOf(RuntimeEvent.ForegroundProbeTick), events)
    }

    @Test
    fun eventDrivenForegroundSkipsDelayedProbeButKeepsImmediateProbe() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)
        val state = RuntimeState(settings = com.gaozay.smartflight.settings.UserSettings(automationEnabled = true))
        val ruleEngine = AutomationRuleEngine(ForegroundRuleEvaluator())

        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = ruleEngine,
            immediate = false,
            eventDrivenForegroundAvailable = true,
        )
        advanceTimeBy(RuntimeTaskScheduler.SCREEN_ON_POLL_INTERVAL_MILLIS)
        runCurrent()
        assertTrue(events.isEmpty())

        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = ruleEngine,
            immediate = true,
            eventDrivenForegroundAvailable = true,
        )
        runCurrent()

        assertEquals(listOf(RuntimeEvent.ForegroundProbeTick), events)
    }

    @Test
    fun runtimeEventNamesIncludeAccessibilityForegroundEvents() {
        assertEquals(
            "ForegroundAppChanged",
            RuntimeEvent.ForegroundAppChanged(
                ForegroundAppInfo("com.example.app", "Example", 1_000L),
            ).nameForLog(),
        )
        assertEquals(
            "ForegroundEventSourceChanged",
            RuntimeEvent.ForegroundEventSourceChanged.nameForLog(),
        )
    }

    @Test
    fun delayedAppExitMarksActiveUntilCancelled() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)

        scheduler.scheduleAppExitDisconnect(delayMillis = 1_000L)
        assertTrue(scheduler.isAppExitDisconnectActive)
        advanceTimeBy(999L)
        runCurrent()
        assertTrue(events.isEmpty())
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(listOf(RuntimeEvent.AppExitDisconnectDue), events)
        scheduler.markAppExitDisconnectConsumed()
        assertFalse(scheduler.isAppExitDisconnectActive)
    }

    @Test
    fun appExitRemainingDelaySubtractsElapsedTime() {
        assertEquals(
            700L,
            DisconnectAutomationHandler.remainingDelayMillis(
                delaySeconds = 1,
                baseTimestampMillis = 1_000L,
                nowMillis = 1_300L,
            ),
        )
        assertEquals(
            0L,
            DisconnectAutomationHandler.remainingDelayMillis(
                delaySeconds = 1,
                baseTimestampMillis = 1_000L,
                nowMillis = 2_500L,
            ),
        )
    }

    private fun scheduler(
        scope: TestScope,
        events: MutableList<RuntimeEvent>,
    ): RuntimeTaskScheduler {
        val runtimeStatusRepository = FakeRuntimeStatusRepository()
        return RuntimeTaskScheduler(
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
            send = { events += it },
            reporter = RuntimeSnapshotReporter(runtimeStatusRepository),
        )
    }
}
