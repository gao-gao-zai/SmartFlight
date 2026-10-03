package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.ResourceLabel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ForegroundInfoSource(override val labelRes: Int) : ResourceLabel {
    Unknown(R.string.activity_source_unknown),
    UsageStats(R.string.usage_stats_polling),
    Accessibility(R.string.accessibility_monitoring),
}

@Singleton
class ForegroundObservationStore @Inject constructor() {
    private val current = MutableStateFlow<ForegroundAppInfo?>(null)
    val latest = current.asStateFlow()
    private val events = MutableSharedFlow<ForegroundAppInfo>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val entries = events.asSharedFlow()

    @Synchronized
    fun accept(candidate: ForegroundAppInfo?): ForegroundAppInfo? {
        val next = chooseForegroundUpdate(current.value, candidate)
        current.value = next
        return next
    }
    fun publishEntry(info: ForegroundAppInfo) {
        if (info.activityConfirmed && info.activityName != null) events.tryEmit(info)
    }
    fun clear() { current.value = null }
}

fun ForegroundAppInfo.confirmedActivity(nowMillis: Long = System.currentTimeMillis()): String? =
    activityName?.takeIf { activityConfirmed && verifiedAtMillis > 0 && nowMillis - verifiedAtMillis in 0..30_000L }

fun ForegroundAppInfo.withoutActivityConfirmation(): ForegroundAppInfo = copy(
    activityName = null, activityConfirmed = false, source = ForegroundInfoSource.Unknown, verifiedAtMillis = 0,
)

internal fun chooseForegroundUpdate(old: ForegroundAppInfo?, candidate: ForegroundAppInfo?): ForegroundAppInfo? {
    if (old == null || candidate == null || candidate.eventTimestampMillis >= old.eventTimestampMillis) return candidate
    if (candidate.source == ForegroundInfoSource.Unknown && candidate.packageName == old.packageName) {
        return old.copy(activityName = null, activityConfirmed = false, source = ForegroundInfoSource.Unknown, verifiedAtMillis = 0)
    }
    return old
}
