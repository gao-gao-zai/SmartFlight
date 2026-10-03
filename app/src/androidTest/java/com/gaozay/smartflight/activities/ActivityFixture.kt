package com.gaozay.smartflight.activities

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*

internal const val ACTIVITY_FIXTURE = "com.gaozay.smartflight.activityfixture"
internal fun activityShell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
).bufferedReader().use { it.readText() }
internal fun installActivityFixture(version: Int = 1) {
    val output = activityShell("pm install -r -d /data/local/tmp/smartflight-activity-v$version.apk")
    assertTrue("Prepare fixture with scripts/run_emulator_tests.sh: $output", output.contains("Success"))
}
internal fun awaitActivityCondition(message: String, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + 15_000
    do {
        if (condition()) return
        SystemClock.sleep(100)
    } while (SystemClock.uptimeMillis() < deadline)
    fail(message)
}
