package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.quickrule.isQuickRuleWindow
import android.content.Context
import com.gaozay.smartflight.activities.DeclaredActivityResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityForegroundAppTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: DeclaredActivityResolver,
    private val observations: ForegroundObservationStore,
) {
    private var latestForegroundApp: ForegroundAppInfo? = null
    private var serviceConnected = false
    private var lastRecordedAtMillis = 0L
    val isServiceConnected: Boolean @Synchronized get() = serviceConnected
    @Synchronized fun markServiceConnected() { serviceConnected = true }
    @Synchronized fun markServiceDisconnected() {
        serviceConnected = false
        latestForegroundApp = null
    }
    @Synchronized fun latest(): ForegroundAppInfo? {
        if (System.currentTimeMillis() < lastRecordedAtMillis) latestForegroundApp = null
        return latestForegroundApp
    }
    @Synchronized fun invalidateActivityConfirmation() { latestForegroundApp = latestForegroundApp?.withoutActivityConfirmation() }

    @Synchronized
    fun recordPackage(packageName: String, eventTimestampMillis: Long, className: String? = null): AccessibilityForegroundAppUpdate? {
        if (isQuickRuleWindow(packageName, className, context.packageName)) return null
        if (packageName.isBlank()) return null
        // A wall-clock adjustment starts a new timestamp epoch; ordinary late events
        // within an epoch must still be rejected.
        val now = System.currentTimeMillis()
        if (now < lastRecordedAtMillis) latestForegroundApp = null
        lastRecordedAtMillis = now
        if (eventTimestampMillis < (latestForegroundApp?.eventTimestampMillis ?: 0)) return null
        // Dialogs, input methods and overlays are not evidence that their package's
        // Activity became foreground. Real declared Activities from any package work.
        val activity = resolver.resolve(packageName, className) ?: return null
        val info = ForegroundAppInfo(packageName, resolveLabel(packageName), eventTimestampMillis, activity,
            ForegroundInfoSource.Accessibility, activity != null, eventTimestampMillis)
        val old = latestForegroundApp
        latestForegroundApp = info
        // This cache belongs exclusively to Accessibility. Only Hybrid selects and
        // writes the observation used by automation for the configured monitor mode.
        observations.publishEntry(info)
        return AccessibilityForegroundAppUpdate(info, old?.packageName != packageName, old?.activityName != activity)
    }
    private fun resolveLabel(packageName: String): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

data class AccessibilityForegroundAppUpdate(val foregroundApp: ForegroundAppInfo, val packageChanged: Boolean, val activityChanged: Boolean = false)
