package com.gaozay.smartflight.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorCommandModelsTest {
    @Test
    fun parseBinaryToggleStateSupportsOneAndZero() {
        assertEquals(true, parseBinaryToggleState("1"))
        assertEquals(false, parseBinaryToggleState("0"))
    }

    @Test
    fun parseBinaryToggleStateRejectsBlankAndInvalidOutput() {
        assertEquals(null, parseBinaryToggleState(""))
        assertEquals(null, parseBinaryToggleState("enabled"))
    }

    @Test
    fun isPhoneServiceUnavailableRecognizesKnownFailureOutputs() {
        assertTrue(isPhoneServiceUnavailable("Service phone: not found", ""))
        assertTrue(isPhoneServiceUnavailable("", "cmd: Can't find service: phone"))
    }

    @Test
    fun isPhoneServiceUnavailableIgnoresHealthyOutputs() {
        assertFalse(isPhoneServiceUnavailable("Service phone: found", ""))
        assertFalse(isPhoneServiceUnavailable("1", ""))
    }
}
