package com.gaozay.smartflight.activities

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gaozay.smartflight.runtime.ForegroundObservationStore
import com.gaozay.smartflight.runtime.HybridForegroundAppSource
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ActivityRecordingState(
    val packageName: String? = null,
    val sessionId: String? = null,
    val startedAtMillis: Long = 0,
    val endedAtMillis: Long? = null,
    val active: Boolean = false,
    val needsManualLaunch: Boolean = false,
    val authorizationMissing: Boolean = false,
)

@Singleton
class ActivityRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ActivityRepository,
    private val foreground: HybridForegroundAppSource,
    private val observations: ForegroundObservationStore,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = MutableStateFlow(ActivityRecordingState())
    val state = current.asStateFlow()
    private var observer: Job? = null
    private var probe: Job? = null
    private var hostPaused = false

    @Synchronized
    fun startObserving() {
        if (observer != null) return
        observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observations.entries.collect { entry ->
                val name = entry.activityName ?: return@collect
                val session = current.value
                val sessionId = session.sessionId?.takeIf {
                    session.packageName == entry.packageName && entry.eventTimestampMillis >= session.startedAtMillis &&
                        (session.endedAtMillis == null || entry.eventTimestampMillis <= session.endedAtMillis)
                }
                try { repository.recordVisit(entry.packageName, name, entry.eventTimestampMillis, entry.source.name, sessionId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("ActivityRecorder", "Unable to persist Activity entry", error) }
            }
        }
    }

    suspend fun start(packageName: String, launch: Boolean) {
        stop()
        val mode = settings.settings.first().foregroundMonitorMode
        if (!foreground.canMonitor(mode)) {
            current.value = ActivityRecordingState(packageName = packageName, authorizationMissing = true)
            return
        }
        startObserving()
        hostPaused = false
        val intent = if (launch) context.packageManager.getLaunchIntentForPackage(packageName) else null
        current.value = ActivityRecordingState(packageName, UUID.randomUUID().toString(), System.currentTimeMillis(),
            active = true, needsManualLaunch = intent == null)
        probe = scope.launch {
            while (isActive) {
                try { foreground.detect(mode, confirm = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("ActivityRecorder", "Activity probe failed", error) }
                delay(700)
            }
        }
        if (intent != null) {
            try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { current.value = current.value.copy(needsManualLaunch = true) }
        }
    }
    fun markHostPaused() { if (current.value.active) hostPaused = true }
    fun stopOnHostReturn() {
        if (!hostPaused || !current.value.active) return
        // Drain usage events once more; timestamp bounds also accept entries queued before return.
        scope.launch { try { foreground.detect(settings.settings.first().foregroundMonitorMode, true) } finally { stop() } }
    }
    fun stop() {
        probe?.cancel()
        probe = null
        current.value = current.value.let { if (it.active) it.copy(active = false, endedAtMillis = System.currentTimeMillis()) else it }
        hostPaused = false
    }
}
