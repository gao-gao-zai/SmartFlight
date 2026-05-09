package com.gaozay.smartflight.runtime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ForegroundAppInfo(
    val packageName: String,
    val appLabel: String,
)

@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var lastEventTimestampMillis: Long = (System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0L)
    private var lastKnownForegroundApp: ForegroundAppInfo? = null

    @Synchronized
    fun detect(): ForegroundAppInfo? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = lastEventTimestampMillis.coerceAtMost(endTime)
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestForegroundPackage: String? = null
        var latestForegroundTimestamp = startTime
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > latestForegroundTimestamp) {
                latestForegroundTimestamp = event.timeStamp
            }
            if (isForegroundEvent(event) && !event.packageName.isNullOrBlank()) {
                latestForegroundPackage = event.packageName
            }
        }
        lastEventTimestampMillis = (latestForegroundTimestamp + 1L).coerceAtMost(endTime + 1L)
        val packageName = latestForegroundPackage ?: return lastKnownForegroundApp
        val appLabel = runCatching {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
        return ForegroundAppInfo(
            packageName = packageName,
            appLabel = appLabel,
        ).also { lastKnownForegroundApp = it }
    }

    private fun isForegroundEvent(event: UsageEvents.Event): Boolean {
        return when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> true
            else -> false
        }
    }

    companion object {
        private const val INITIAL_LOOKBACK_MILLIS = 10_000L
    }
}
