package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HybridForegroundAppSourceTest : LocalizedStringsTest() {
    @Test
    fun configuredActivitiesSupplementAccessibilityWithNewerUsageConfirmation() {
        val accessibility = ForegroundAppInfo("same.app", "Same", 1000, "same.app.First", ForegroundInfoSource.Accessibility, true, 1000)
        val usage = accessibility.copy(activityName = "same.app.Second", source = ForegroundInfoSource.UsageStats, eventTimestampMillis = 1100)
        assertEquals(usage, detectHybridForegroundApp(ForegroundMonitorMode.Auto, true, { accessibility }, { usage }, true))
        assertEquals(accessibility, detectHybridForegroundApp(ForegroundMonitorMode.Auto, true, { accessibility }, { usage.copy(eventTimestampMillis = 900) }, true))
    }

    @Test
    fun noActivityRulesPreserveTheLowCostAccessibilityPath() {
        var calls = 0
        val accessibility = ForegroundAppInfo("same.app", "Same", 1000)
        assertEquals(accessibility, detectHybridForegroundApp(ForegroundMonitorMode.Auto, true, { accessibility }, { calls++; null }))
        assertEquals(0, calls)
    }

    @Test
    fun accessibilityOnlyModeNeverUsesUsageConfirmationForActivityRules() {
        var calls = 0
        detectHybridForegroundApp(ForegroundMonitorMode.Accessibility, true, { null }, { calls++; null }, true)
        assertEquals(0, calls)
    }

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
