package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.i18n.LocalizedStringsTest
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
class RuntimeTaskSchedulerTest : LocalizedStringsTest() {
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

        val event = events.single() as RuntimeEvent.AppExitDisconnectDue
        assertTrue(scheduler.isAppExitDisconnectActive)
        assertTrue(scheduler.markAppExitDisconnectConsumed(event))
        assertFalse(scheduler.isAppExitDisconnectActive)
    }

    @Test
    fun staleQueuedAppExitCannotConsumeReplacementOrRunBeforeItsDeadline() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)
        scheduler.scheduleAppExitDisconnect(30_000L)
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        val stale = events.single() as RuntimeEvent.AppExitDisconnectDue
        assertTrue(scheduler.isAppExitDisconnectDueCurrent(stale))

        scheduler.cancelAppExitDisconnect()
        scheduler.scheduleAppExitDisconnect(30_000L)
        runCurrent()
        assertFalse(scheduler.markAppExitDisconnectConsumed(stale))
        assertTrue(scheduler.isAppExitDisconnectActive)
        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(1, events.size)
        advanceTimeBy(1L)
        runCurrent()
        val replacement = events.last() as RuntimeEvent.AppExitDisconnectDue
        assertFalse(scheduler.markAppExitDisconnectConsumed(stale))
        assertTrue(scheduler.markAppExitDisconnectConsumed(replacement))
        assertFalse(scheduler.markAppExitDisconnectConsumed(replacement))
    }

    @Test
    fun matchingTokenStillRequiresMonotonicDeadline() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        var now = 1_000L
        val scheduler = RuntimeTaskScheduler(
            scope = backgroundScope,
            send = { events += it },
            reporter = RuntimeSnapshotReporter(FakeRuntimeStatusRepository()),
            monotonicTimeMillis = { now },
        )
        scheduler.scheduleAppExitDisconnect(30_000L)
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()
        val event = events.single() as RuntimeEvent.AppExitDisconnectDue
        assertFalse(scheduler.markAppExitDisconnectConsumed(event))
        assertTrue(scheduler.isAppExitDisconnectActive)
        now = 30_999L
        assertFalse(scheduler.markAppExitDisconnectConsumed(event))
        now = 31_000L
        assertTrue(scheduler.markAppExitDisconnectConsumed(event))
    }

    @Test
    fun staleQueuedScreenOffCannotConsumeReplacementAndStopInvalidatesBothKinds() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)
        scheduler.scheduleScreenOffDisconnect(60_000L)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        val stale = events.single() as RuntimeEvent.ScreenOffDisconnectDue
        scheduler.cancelScreenOffDisconnect()
        scheduler.scheduleScreenOffDisconnect(60_000L)
        runCurrent()
        assertFalse(scheduler.markScreenOffDisconnectConsumed(stale))
        assertTrue(scheduler.isScreenOffDisconnectActive)
        advanceTimeBy(60_000L)
        runCurrent()
        val replacement = events.last() as RuntimeEvent.ScreenOffDisconnectDue
        scheduler.scheduleAppExitDisconnect(0L)
        runCurrent()
        val appExit = events.last() as RuntimeEvent.AppExitDisconnectDue
        scheduler.cancelAll()
        assertFalse(scheduler.markScreenOffDisconnectConsumed(replacement))
        assertFalse(scheduler.markAppExitDisconnectConsumed(appExit))
        assertFalse(scheduler.isScreenOffDisconnectActive)
        assertFalse(scheduler.isAppExitDisconnectActive)
    }

    @Test
    fun repeatedRapidTransitionsOnlyLeaveLatestTaskValid() = runTest {
        val events = mutableListOf<RuntimeEvent>()
        val scheduler = scheduler(this, events)
        repeat(20) {
            scheduler.scheduleAppExitDisconnect(0L)
            scheduler.scheduleScreenOffDisconnect(0L)
            runCurrent()
            scheduler.cancelAppExitDisconnect()
            scheduler.cancelScreenOffDisconnect()
        }
        scheduler.scheduleAppExitDisconnect(1_000L)
        scheduler.scheduleScreenOffDisconnect(1_000L)
        runCurrent()
        events.forEach {
            when (it) {
                is RuntimeEvent.AppExitDisconnectDue -> assertFalse(scheduler.markAppExitDisconnectConsumed(it))
                is RuntimeEvent.ScreenOffDisconnectDue -> assertFalse(scheduler.markScreenOffDisconnectConsumed(it))
                else -> error("Unexpected event")
            }
        }
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(scheduler.markAppExitDisconnectConsumed(events[events.size - 2] as RuntimeEvent.AppExitDisconnectDue))
        assertTrue(scheduler.markScreenOffDisconnectConsumed(events.last() as RuntimeEvent.ScreenOffDisconnectDue))
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
            monotonicTimeMillis = { scope.testScheduler.currentTime },
        )
    }
}
