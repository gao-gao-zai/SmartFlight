package com.gaozay.smartflight.runtime

import org.junit.Assert.*
import org.junit.Test

class RuntimeCallbackGateTest {
    @Test fun callbacksOnlyNotifyTheirOwnActiveRegistration() {
        val gate = RuntimeCallbackGate()
        var oldCalls = 0
        var newCalls = 0
        val old = gate.register { oldCalls++ }
        gate.dispatch(old)
        assertEquals(1, oldCalls)
        gate.clear()
        gate.dispatch(old)
        val replacement = gate.register { newCalls++ }
        gate.dispatch(old)
        assertEquals(0, newCalls)
        gate.dispatch(replacement)
        assertEquals(1, newCalls)
        gate.clear()
        gate.dispatch(replacement)
        assertEquals(1, newCalls)
    }
}
