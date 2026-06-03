package com.gaozay.smartflight.runtime

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartFlightAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var foregroundAppTracker: AccessibilityForegroundAppTracker

    @Inject
    lateinit var runtimeCoordinator: AutomationRuntimeCoordinator

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
        val update = foregroundAppTracker.recordPackage(
            packageName = packageName,
            eventTimestampMillis = System.currentTimeMillis(),
        ) ?: return
        if (update.packageChanged) {
            runtimeCoordinator.onForegroundAppChanged(update.foregroundApp)
        }
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
