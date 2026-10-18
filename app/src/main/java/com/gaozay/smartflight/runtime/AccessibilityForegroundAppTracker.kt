package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.quickrule.isQuickRuleWindow
import android.content.Context
import com.gaozay.smartflight.activities.DeclaredActivityResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityForegroundAppTracker internal constructor(
    private val selfPackageName: String,
    private val resolve: (String, String?) -> String?,
    private val observations: ForegroundObservationStore,
    private val resolveLabel: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@ApplicationContext context: Context, resolver: DeclaredActivityResolver, observations: ForegroundObservationStore) :
        this(context.packageName, resolver::resolve, observations, { pkg -> kotlin.runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg) })

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
        if (clock() < lastRecordedAtMillis) latestForegroundApp = null
        return latestForegroundApp
    }
    @Synchronized fun invalidateActivityConfirmation() { latestForegroundApp = latestForegroundApp?.withoutActivityConfirmation() }

    @Synchronized
    fun recordPackage(packageName: String, eventTimestampMillis: Long, className: String? = null): AccessibilityForegroundAppUpdate? {
        if (isQuickRuleWindow(packageName, className, selfPackageName)) return null
        if (packageName.isBlank()) return null
        // A wall-clock adjustment starts a new timestamp epoch; ordinary late events
        // within an epoch must still be rejected.
        val now = clock()
        if (now < lastRecordedAtMillis) latestForegroundApp = null
        lastRecordedAtMillis = now
        if (eventTimestampMillis < (latestForegroundApp?.eventTimestampMillis ?: 0)) return null
        // Dialogs, input methods and overlays are not evidence that their package's
        // Activity became foreground. Real declared Activities from any package work.
        val activity = resolve(packageName, className) ?: return null
        val info = ForegroundAppInfo(packageName, resolveLabel(packageName), eventTimestampMillis, activity,
            ForegroundInfoSource.Accessibility, true, eventTimestampMillis)
        val old = latestForegroundApp
        latestForegroundApp = info
        // This cache belongs exclusively to Accessibility. Only Hybrid selects and
        // writes the observation used by automation for the configured monitor mode.
        observations.publishEntry(info)
        return AccessibilityForegroundAppUpdate(info, old?.packageName != packageName, old?.activityName != activity)
    }
}

data class AccessibilityForegroundAppUpdate(val foregroundApp: ForegroundAppInfo, val packageChanged: Boolean, val activityChanged: Boolean = false)
