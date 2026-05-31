package com.gaozay.smartflight.runtime

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeExpectedNetworkChangeTracker @Inject constructor() {
    private var expectedChange: ExpectedNetworkChange? = null

    fun record(targetDisconnected: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        expectedChange = ExpectedNetworkChange(
            targetDisconnected = targetDisconnected,
            expiresAtMillis = nowMillis + EXPECTED_CHANGE_WINDOW_MILLIS,
        )
    }

    fun consumeIfExpected(
        observedDisconnected: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val expected = expectedChange ?: return false
        if (nowMillis > expected.expiresAtMillis) {
            expectedChange = null
            return false
        }
        if (expected.targetDisconnected != observedDisconnected) {
            return false
        }
        expectedChange = null
        return true
    }

    fun clearExpired(nowMillis: Long = System.currentTimeMillis()) {
        val expected = expectedChange ?: return
        if (nowMillis > expected.expiresAtMillis) {
            expectedChange = null
        }
    }

    private data class ExpectedNetworkChange(
        val targetDisconnected: Boolean,
        val expiresAtMillis: Long,
    )

    companion object {
        const val EXPECTED_CHANGE_WINDOW_MILLIS = 5_000L
    }
}
