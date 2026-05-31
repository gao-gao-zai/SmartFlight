package com.gaozay.smartflight.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeExpectedNetworkChangeTrackerTest {
    @Test
    fun consumesMatchingExpectedChangeWithinWindow() {
        val tracker = RuntimeExpectedNetworkChangeTracker()

        tracker.record(targetDisconnected = true, nowMillis = 1_000L)

        assertTrue(tracker.consumeIfExpected(observedDisconnected = true, nowMillis = 2_000L))
        assertFalse(tracker.consumeIfExpected(observedDisconnected = true, nowMillis = 2_001L))
    }

    @Test
    fun rejectsMismatchedOrExpiredChange() {
        val tracker = RuntimeExpectedNetworkChangeTracker()

        tracker.record(targetDisconnected = true, nowMillis = 1_000L)

        assertFalse(tracker.consumeIfExpected(observedDisconnected = false, nowMillis = 2_000L))
        assertFalse(tracker.consumeIfExpected(observedDisconnected = true, nowMillis = 7_001L))
    }
}
