package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.ForegroundMonitorMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridForegroundAppSource @Inject constructor(
    private val accessibilityForegroundAppTracker: AccessibilityForegroundAppTracker,
    private val usageStatsForegroundAppDetector: ForegroundAppDetector,
    private val observations: ForegroundObservationStore,
) : ForegroundAppSource {
    @Volatile var monitorMode: ForegroundMonitorMode = ForegroundMonitorMode.Auto
        set(value) {
            if (field != value) {
                usageStatsForegroundAppDetector.invalidateActivityConfirmation()
                accessibilityForegroundAppTracker.invalidateActivityConfirmation()
                observations.clear()
            }
            field = value
        }
    @Volatile var confirmActivity: Boolean = false
    fun canMonitor(mode: ForegroundMonitorMode): Boolean = when (mode) {
        ForegroundMonitorMode.Auto -> accessibilityForegroundAppTracker.isServiceConnected || usageStatsForegroundAppDetector.hasPermission()
        ForegroundMonitorMode.Accessibility -> accessibilityForegroundAppTracker.isServiceConnected
        ForegroundMonitorMode.UsageStats -> usageStatsForegroundAppDetector.hasPermission()
    }
    override fun detect(): ForegroundAppInfo? = detect(monitorMode, confirmActivity)
    fun detect(mode: ForegroundMonitorMode, confirm: Boolean): ForegroundAppInfo? = observations.accept(
        detectHybridForegroundApp(mode, accessibilityForegroundAppTracker.isServiceConnected,
            accessibilityForegroundAppTracker::latest, usageStatsForegroundAppDetector::detect, confirm),
    )
}

internal fun detectHybridForegroundApp(
    monitorMode: ForegroundMonitorMode,
    accessibilityConnected: Boolean,
    accessibilityLatest: () -> ForegroundAppInfo?,
    usageStatsDetect: () -> ForegroundAppInfo?,
    confirmActivity: Boolean = false,
): ForegroundAppInfo? = when (monitorMode) {
    ForegroundMonitorMode.Auto -> {
        val accessibility = if (accessibilityConnected) accessibilityLatest() else null
        if (accessibility == null) usageStatsDetect()
        else if (!confirmActivity) accessibility
        else {
            val usage = usageStatsDetect()
            when {
                usage == null || usage.source == ForegroundInfoSource.Unknown -> accessibility
                usage.eventTimestampMillis > accessibility.eventTimestampMillis -> usage
                usage.eventTimestampMillis == accessibility.eventTimestampMillis && usage.activityConfirmed -> usage
                else -> accessibility
            }
        }
    }
    ForegroundMonitorMode.Accessibility -> if (accessibilityConnected) accessibilityLatest() else null
    ForegroundMonitorMode.UsageStats -> usageStatsDetect()
}
