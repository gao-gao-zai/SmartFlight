package com.gaozay.smartflight.runtime

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartFlightAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var foregroundAppTracker: AccessibilityForegroundAppTracker

    @Inject
    lateinit var runtimeCoordinator: AutomationRuntimeCoordinator

    private data class WindowEvent(val packageName: String, val className: String?, val timestamp: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val windows = Channel<WindowEvent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (event in windows) {
                try {
                    val update = foregroundAppTracker.recordPackage(event.packageName, event.timestamp, event.className) ?: continue
                    if (update.packageChanged || update.activityChanged) runtimeCoordinator.onForegroundAppChanged(update.foregroundApp)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w(LOG_TAG, "Unable to validate foreground Activity", error) }
            }
        }
    }
    override fun onDestroy() {
        windows.close()
        scope.cancel()
        foregroundAppTracker.markServiceDisconnected()
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        foregroundAppTracker.markServiceConnected()
        runtimeCoordinator.onForegroundEventSourceChanged()
        Log.d(LOG_TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isForegroundWindowEvent(event.eventType)) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        val timestamp = System.currentTimeMillis() - (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0)
        windows.trySend(WindowEvent(packageName, event.className?.toString(), timestamp))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        foregroundAppTracker.markServiceDisconnected()
        runtimeCoordinator.onForegroundEventSourceChanged()
        Log.d(LOG_TAG, "accessibility service disconnected")
        return super.onUnbind(intent)
    }

    private fun isForegroundWindowEvent(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

    private companion object {
        const val LOG_TAG = "SmartFlightA11y"
    }
}
