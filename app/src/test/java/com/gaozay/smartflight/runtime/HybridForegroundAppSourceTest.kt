package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.ForegroundMonitorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HybridForegroundAppSourceTest {
    @Test
    fun accessibilityModeDoesNotCallUsageStatsFallbackWhenCacheIsEmpty() {
        var usageStatsDetectCalls = 0

        val result = detectHybridForegroundApp(
            monitorMode = ForegroundMonitorMode.Accessibility,
            accessibilityConnected = true,
            accessibilityLatest = { null },
            usageStatsDetect = {
                usageStatsDetectCalls++
                ForegroundAppInfo("com.example.fallback", "Fallback", 1_000L)
            },
        )

        assertNull(result)
        assertEquals(0, usageStatsDetectCalls)
    }

    @Test
    fun autoModeFallsBackToUsageStatsWhenAccessibilityCacheIsEmpty() {
        var usageStatsDetectCalls = 0

        val result = detectHybridForegroundApp(
            monitorMode = ForegroundMonitorMode.Auto,
            accessibilityConnected = true,
            accessibilityLatest = { null },
            usageStatsDetect = {
                usageStatsDetectCalls++
                ForegroundAppInfo("com.example.fallback", "Fallback", 1_000L)
            },
        )

        assertEquals("com.example.fallback", result?.packageName)
        assertEquals(1, usageStatsDetectCalls)
    }
}
