package com.gaozay.smartflight.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessGateStateTest {
    @Test
    fun canEnterWithAdvancedAccessAndUsageStatsOnly() {
        val state = AccessGateState(
            advancedAccess = availableAdvancedAccess(),
            usageStatsAccess = granted("使用情况访问权限"),
            accessibilityAccess = missing("无障碍前台监听"),
        )

        assertTrue(state.foregroundDetectionAvailable)
        assertTrue(state.canEnterApp)
    }

    @Test
    fun canEnterWithAdvancedAccessAndAccessibilityOnly() {
        val state = AccessGateState(
            advancedAccess = availableAdvancedAccess(),
            usageStatsAccess = missing("使用情况访问权限"),
            accessibilityAccess = granted("无障碍前台监听"),
        )

        assertTrue(state.foregroundDetectionAvailable)
        assertTrue(state.canEnterApp)
    }

    @Test
    fun cannotEnterWithoutAnyForegroundDetectionAccess() {
        val state = AccessGateState(
            advancedAccess = availableAdvancedAccess(),
            usageStatsAccess = missing("使用情况访问权限"),
            accessibilityAccess = missing("无障碍前台监听"),
        )

        assertFalse(state.foregroundDetectionAvailable)
        assertFalse(state.canEnterApp)
        assertTrue(state.blockingChecks.any { it.title == "无障碍前台监听" })
        assertTrue(state.blockingChecks.any { it.title == "使用情况访问权限" })
    }

    private fun availableAdvancedAccess(): AdvancedAccessState =
        AdvancedAccessState(
            checks = listOf(
                AccessCheckResult(
                    title = "Root",
                    status = AccessCheckStatus.Ready,
                    summary = "可用",
                    recommendation = "",
                    isBlocking = true,
                ),
            ),
        )

    private fun granted(title: String): AccessCheckResult =
        AccessCheckResult(
            title = title,
            status = AccessCheckStatus.Granted,
            summary = "已授权",
            recommendation = "",
            isBlocking = true,
        )

    private fun missing(title: String): AccessCheckResult =
        AccessCheckResult(
            title = title,
            status = AccessCheckStatus.Missing,
            summary = "未授权",
            recommendation = "",
            isBlocking = true,
        )
}
