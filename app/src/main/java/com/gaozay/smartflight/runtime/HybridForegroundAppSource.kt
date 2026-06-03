package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.ForegroundMonitorMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridForegroundAppSource @Inject constructor(
    private val accessibilityForegroundAppTracker: AccessibilityForegroundAppTracker,
    private val usageStatsForegroundAppDetector: ForegroundAppDetector,
) : ForegroundAppSource {
    @Volatile
    var monitorMode: ForegroundMonitorMode = ForegroundMonitorMode.Auto

    override fun detect(): ForegroundAppInfo? =
        detectHybridForegroundApp(
            monitorMode = monitorMode,
            accessibilityConnected = accessibilityForegroundAppTracker.isServiceConnected,
            accessibilityLatest = accessibilityForegroundAppTracker::latest,
            usageStatsDetect = usageStatsForegroundAppDetector::detect,
        )
}

internal fun detectHybridForegroundApp(
    monitorMode: ForegroundMonitorMode,
    accessibilityConnected: Boolean,
    accessibilityLatest: () -> ForegroundAppInfo?,
    usageStatsDetect: () -> ForegroundAppInfo?,
): ForegroundAppInfo? =
    when (monitorMode) {
        ForegroundMonitorMode.Auto -> {
            if (accessibilityConnected) {
                accessibilityLatest() ?: usageStatsDetect()
            } else {
                usageStatsDetect()
            }
        }
        ForegroundMonitorMode.Accessibility -> accessibilityLatest()
        ForegroundMonitorMode.UsageStats -> usageStatsDetect()
    }
