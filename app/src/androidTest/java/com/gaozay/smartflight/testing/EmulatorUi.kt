package com.gaozay.smartflight.testing

import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo

/** Cold Google APIs images sometimes show a Launcher ANR over an otherwise ready test app. */
internal fun UiAutomation.dismissLauncherAnrIfPresent(): Boolean {
    val root = rootInActiveWindow ?: return false
    if (root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isEmpty()) return false
    // Never dismiss an ANR from SmartFlight or the fixture under test.
    val close = root.findAccessibilityNodeInfosByText("Close app").firstOrNull() ?: return false
    return close.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}
