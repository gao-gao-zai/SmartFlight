package com.gaozay.smartflight.runtime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ForegroundAppInfo(
    val packageName: String,
    val appLabel: String,
    val eventTimestampMillis: Long,
)

interface ForegroundAppSource {
    fun detect(): ForegroundAppInfo?
}

@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundAppSource {
    private var lastEventTimestampMillis: Long = (System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0L)
    private var lastKnownForegroundApp: ForegroundAppInfo? = null

    @Synchronized
    override fun detect(): ForegroundAppInfo? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = lastEventTimestampMillis.coerceAtMost(endTime)
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestForegroundPackage: String? = null
        var latestForegroundTimestamp = startTime
        var processedEvents = 0
        var resumedEvents = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            processedEvents++
            if (event.timeStamp > latestForegroundTimestamp) {
                latestForegroundTimestamp = event.timeStamp
            }
            if (isForegroundEvent(event) && !event.packageName.isNullOrBlank()) {
                resumedEvents++
                latestForegroundPackage = event.packageName
            }
        }
        lastEventTimestampMillis = (latestForegroundTimestamp + 1L).coerceAtMost(endTime + 1L)
        val packageName = latestForegroundPackage ?: return lastKnownForegroundApp.also {
            Log.d(
                LOG_TAG,
                "detect fallback processed=$processedEvents resumed=$resumedEvents start=$startTime end=$endTime latestTs=$latestForegroundTimestamp fallbackPkg=${it?.packageName ?: "<none>"}",
            )
        }
        val appLabel = runCatching {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
        return ForegroundAppInfo(
            packageName = packageName,
            appLabel = appLabel,
            eventTimestampMillis = latestForegroundTimestamp,
        ).also {
            lastKnownForegroundApp = it
            Log.d(
                LOG_TAG,
                "detect resolved processed=$processedEvents resumed=$resumedEvents start=$startTime end=$endTime latestTs=$latestForegroundTimestamp pkg=${it.packageName} label=${it.appLabel}",
            )
        }
    }

    private fun isForegroundEvent(event: UsageEvents.Event): Boolean {
        return when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> true
            else -> false
        }
    }

    companion object {
        private const val LOG_TAG = "SmartFlightFgDetect"
        private const val INITIAL_LOOKBACK_MILLIS = 10_000L
    }
}
