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
    val isServiceConnected: Boolean @Synchronized get() = serviceConnected
    @Synchronized fun markServiceConnected() { serviceConnected = true }
    @Synchronized fun markServiceDisconnected() {
        serviceConnected = false
        latestForegroundApp = null
        observations.clear()
    }
    @Synchronized fun latest(): ForegroundAppInfo? = latestForegroundApp
    @Synchronized fun invalidateActivityConfirmation() { latestForegroundApp = latestForegroundApp?.withoutActivityConfirmation() }

    @Synchronized
    fun recordPackage(packageName: String, eventTimestampMillis: Long, className: String? = null): AccessibilityForegroundAppUpdate? {
        if (isQuickRuleWindow(packageName, className, context.packageName)) return null
        if (packageName.isBlank() || eventTimestampMillis < (latestForegroundApp?.eventTimestampMillis ?: 0)) return null
        val activity = resolver.resolve(packageName, className)
        // Ignore unconfirmed dialog/widget events within the same app, including WINDOWS_CHANGED.
        if (activity == null && (latestForegroundApp?.packageName == packageName || packageName == "com.android.systemui")) return null
        val info = ForegroundAppInfo(packageName, resolveLabel(packageName), eventTimestampMillis, activity,
            ForegroundInfoSource.Accessibility, activity != null, eventTimestampMillis)
        val old = latestForegroundApp
        latestForegroundApp = info
        observations.accept(info)
        observations.publishEntry(info)
        return AccessibilityForegroundAppUpdate(info, old?.packageName != packageName, old?.activityName != activity)
    }
    private fun resolveLabel(packageName: String): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}

data class AccessibilityForegroundAppUpdate(val foregroundApp: ForegroundAppInfo, val packageChanged: Boolean, val activityChanged: Boolean = false)
