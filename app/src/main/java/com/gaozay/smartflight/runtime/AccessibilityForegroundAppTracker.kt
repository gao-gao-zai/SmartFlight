package com.gaozay.smartflight.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityForegroundAppTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var latestForegroundApp: ForegroundAppInfo? = null
    private var serviceConnected: Boolean = false

    val isServiceConnected: Boolean
        @Synchronized get() = serviceConnected

    @Synchronized
    fun markServiceConnected() {
        serviceConnected = true
    }

    @Synchronized
    fun markServiceDisconnected() {
        serviceConnected = false
    }

    @Synchronized
    fun latest(): ForegroundAppInfo? = latestForegroundApp

    @Synchronized
    fun recordPackage(
        packageName: String,
        eventTimestampMillis: Long,
    ): AccessibilityForegroundAppUpdate? {
        if (packageName.isBlank()) {
            return null
        }
        val appInfo = ForegroundAppInfo(
            packageName = packageName,
            appLabel = resolveAppLabel(packageName),
            eventTimestampMillis = eventTimestampMillis,
        )
        val previousPackageName = latestForegroundApp?.packageName
        latestForegroundApp = appInfo
        val changed = previousPackageName != packageName
        Log.d(
            LOG_TAG,
            "accessibility foreground pkg=$packageName changed=$changed timestamp=$eventTimestampMillis",
        )
        return AccessibilityForegroundAppUpdate(appInfo, changed)
    }

    private fun resolveAppLabel(packageName: String): String =
        runCatching {
            val applicationInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA,
            )
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)

    private companion object {
        const val LOG_TAG = "SmartFlightA11y"
    }
}

data class AccessibilityForegroundAppUpdate(
    val foregroundApp: ForegroundAppInfo,
    val packageChanged: Boolean,
)
