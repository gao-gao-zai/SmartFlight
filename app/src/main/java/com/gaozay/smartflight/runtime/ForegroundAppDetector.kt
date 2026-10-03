package com.gaozay.smartflight.runtime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.gaozay.smartflight.activities.DeclaredActivityResolver
import com.gaozay.smartflight.activities.normalizeActivityName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ForegroundAppInfo(
    val packageName: String,
    val appLabel: String,
    val eventTimestampMillis: Long,
    val activityName: String? = null,
    val source: ForegroundInfoSource = ForegroundInfoSource.Unknown,
    val activityConfirmed: Boolean = false,
    val verifiedAtMillis: Long = 0,
)

interface ForegroundAppSource { fun detect(): ForegroundAppInfo? }

@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: DeclaredActivityResolver,
    private val observations: ForegroundObservationStore,
) : ForegroundAppSource {
    private var cursorMillis = (System.currentTimeMillis() - 10_000).coerceAtLeast(0)
    private val cursorKeys = mutableSetOf<String>()
    private var lastKnown: ForegroundAppInfo? = null

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Synchronized
    fun invalidateActivityConfirmation() { lastKnown = lastKnown?.withoutActivityConfirmation() }

    @Synchronized
    override fun detect(): ForegroundAppInfo? {
        val now = System.currentTimeMillis()
        if (!hasPermission()) return lastKnown?.copy(activityName = null, activityConfirmed = false,
            source = ForegroundInfoSource.Unknown, verifiedAtMillis = 0)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(cursorMillis.coerceAtMost(now), now) ?: return null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp < cursorMillis) continue
            if (event.timeStamp > cursorMillis) { cursorMillis = event.timeStamp; cursorKeys.clear() }
            val key = "${event.eventType}:${event.packageName}:${event.className}"
            if (!cursorKeys.add(key)) continue
            val pkg = event.packageName?.takeIf { it.isNotBlank() } ?: continue
            // ACTIVITY_RESUMED has the same value as MOVE_TO_FOREGROUND on API 26-28.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val name = resolver.resolve(pkg, event.className) ?: normalizeActivityName(pkg, event.className)
                val info = ForegroundAppInfo(pkg, label(pkg), event.timeStamp, name,
                    ForegroundInfoSource.UsageStats, name != null, now)
                lastKnown = info
                observations.publishEntry(info)
            }
        }
        return lastKnown?.copy(verifiedAtMillis = now).also { lastKnown = it }
    }

    private fun label(pkg: String): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
