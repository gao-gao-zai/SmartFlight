package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.quickrule.isQuickRuleOverlay
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

internal data class ForegroundUsageEvent(val packageName: String?, val className: String?, val timeStamp: Long, val eventType: Int)

@Singleton
class ForegroundAppDetector internal constructor(
    private val selfPackageName: String,
    private val resolve: (String, String?) -> String?,
    private val observations: ForegroundObservationStore,
    private val permission: () -> Boolean,
    private val query: (Long, Long) -> Sequence<ForegroundUsageEvent>?,
    private val label: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ForegroundAppSource {
    @Inject constructor(
        @ApplicationContext context: Context,
        resolver: DeclaredActivityResolver,
        observations: ForegroundObservationStore,
    ) : this(context.packageName, resolver::resolve, observations, { usagePermission(context) },
        { from, to ->
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            manager.queryEvents(from, to)?.let { events -> sequence {
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    yield(ForegroundUsageEvent(event.packageName, event.className, event.timeStamp, event.eventType))
                }
            } }
        }, { pkg -> kotlin.runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg) })
    private var cursorMillis = (clock() - INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0)
    private val cursorKeys = mutableSetOf<String>()
    private var lastKnown: ForegroundAppInfo? = null
    private var pausedForeground: ForegroundAppInfo? = null
    private var quickRuleCovering = false
    private var lastQueryMillis = 0L
    private var permissionLost = false
    @Volatile var latestLifecycleTimestampMillis: Long = 0L
        private set

    fun hasPermission(): Boolean = runCatching(permission).getOrDefault(false)

    @Synchronized
    fun invalidateActivityConfirmation() {
        lastKnown = lastKnown?.withoutActivityConfirmation()
        pausedForeground = pausedForeground?.withoutActivityConfirmation()
    }

    @Synchronized
    override fun detect(): ForegroundAppInfo? {
        return try {
            detectAvailableForeground()
        } catch (_: RuntimeException) {
            // Permission may be revoked between AppOps and queryEvents, and the
            // system service may disappear. Neither failure proves an app exit.
            permissionLost = true
            lastKnown = null
            pausedForeground = null
            quickRuleCovering = false
            latestLifecycleTimestampMillis = 0L
            null
        }
    }

    private fun detectAvailableForeground(): ForegroundAppInfo? {
        val now = clock()
        if (!hasPermission()) {
            permissionLost = true
            lastKnown = null
            pausedForeground = null
            quickRuleCovering = false
            latestLifecycleTimestampMillis = 0L
            return null
        }
        if (permissionLost || now < lastQueryMillis) {
            // Rebuild the lifecycle after permission loss or a clock adjustment;
            // cached evidence from the previous epoch is no longer sufficient.
            cursorMillis = (now - INITIAL_LOOKBACK_MILLIS).coerceAtLeast(0)
            cursorKeys.clear()
            lastKnown = null
            pausedForeground = null
            quickRuleCovering = false
            latestLifecycleTimestampMillis = 0L
            permissionLost = false
        }
        lastQueryMillis = now
        val events = query(cursorMillis.coerceAtMost(now), now) ?: return null
        for (event in events) {
            if (event.timeStamp < cursorMillis) continue
            if (event.timeStamp > cursorMillis) { cursorMillis = event.timeStamp; cursorKeys.clear() }
            val key = "${event.eventType}:${event.packageName}:${event.className}"
            if (!cursorKeys.add(key)) continue
            val pkg = event.packageName?.takeIf { it.isNotBlank() } ?: continue
            if (isQuickRuleOverlay(pkg, event.className, selfPackageName)) {
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    // Our translucent editor can pause the underlying Activity.
                    // Restore only the immediately paused, not stopped, candidate.
                    lastKnown = lastKnown ?: pausedForeground
                    pausedForeground = null
                    quickRuleCovering = true
                } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                    quickRuleCovering = false
                }
                continue
            }
            // ACTIVITY_RESUMED has the same value as MOVE_TO_FOREGROUND on API 26-28.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val name = resolve(pkg, event.className) ?: normalizeActivityName(pkg, event.className)
                val info = ForegroundAppInfo(pkg, label(pkg), event.timeStamp, name,
                    ForegroundInfoSource.UsageStats, name != null, now)
                lastKnown = info
                pausedForeground = null
                quickRuleCovering = false
                latestLifecycleTimestampMillis = event.timeStamp
                observations.publishEntry(info)
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                val candidate = lastKnown ?: pausedForeground
                if (candidate?.packageName == pkg &&
                    (candidate.activityName == null || event.className.isNullOrBlank() ||
                        normalizeActivityName(pkg, event.className) == candidate.activityName ||
                        resolve(pkg, event.className) == candidate.activityName)) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                        pausedForeground = candidate
                        lastKnown = candidate.takeIf { quickRuleCovering }
                    } else {
                        lastKnown = null
                        pausedForeground = null
                        quickRuleCovering = false
                    }
                    latestLifecycleTimestampMillis = event.timeStamp
                }
            }
        }
        return lastKnown?.copy(verifiedAtMillis = now).also { lastKnown = it }
    }

    private companion object {
        fun usagePermission(context: Context): Boolean = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: RuntimeException) { false }

        const val INITIAL_LOOKBACK_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
