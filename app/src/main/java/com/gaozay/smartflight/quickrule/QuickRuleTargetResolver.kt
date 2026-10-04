package com.gaozay.smartflight.quickrule

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gaozay.smartflight.activities.DeclaredActivityResolver
import com.gaozay.smartflight.runtime.*
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class QuickRuleTargetResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibility: AccessibilityForegroundAppTracker,
    private val usagePermission: ForegroundAppDetector,
    private val declarations: DeclaredActivityResolver,
    private val settings: SettingsRepository,
) {
    suspend fun capture(): ForegroundAppInfo? = withContext(Dispatchers.IO) {
        val mode = settings.settings.first().foregroundMonitorMode
        val a11y = if (accessibility.isServiceConnected && mode != ForegroundMonitorMode.UsageStats) accessibility.latest() else null
        val usage = if (mode != ForegroundMonitorMode.Accessibility && usagePermission.hasPermission()) queryBeforeOverlay() else null
        val info = when {
            a11y == null -> usage
            usage == null -> a11y
            usage.eventTimestampMillis >= a11y.eventTimestampMillis -> usage
            else -> a11y
        } ?: return@withContext null
        // Never silently configure our editor, the notification shade, or SmartFlight itself.
        if (info.packageName == context.packageName || info.packageName == "com.android.systemui") return@withContext null
        val activity = info.confirmedActivity()?.let { declarations.resolve(info.packageName, it) }
        info.copy(activityName = activity, activityConfirmed = activity != null)
    }

    private fun queryBeforeOverlay(): ForegroundAppInfo? {
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // An app may have stayed foreground for hours. Querying independently also handles cold starts.
        val events = manager.queryEvents((now - 86_400_000L).coerceAtLeast(0), now) ?: return null
        val event = UsageEvents.Event()
        var latest: ForegroundAppInfo? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = event.packageName ?: continue
            if (pkg == "com.android.systemui" || isQuickRuleOverlay(pkg, event.className, context.packageName)) continue
            // Keep MainActivity as a boundary: opening from SmartFlight must not edit an older app.
            latest = ForegroundAppInfo(pkg, pkg, event.timeStamp, event.className, ForegroundInfoSource.UsageStats,
                event.className != null, now)
        }
        return latest
    }
}
