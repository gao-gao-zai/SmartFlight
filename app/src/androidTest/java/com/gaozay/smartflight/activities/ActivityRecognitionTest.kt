package com.gaozay.smartflight.activities

import android.app.UiAutomation
import android.Manifest
import android.content.ComponentName
import android.provider.Settings
import java.io.File
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.apps.AppSyncTestEntryPoint
import com.gaozay.smartflight.runtime.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityRecognitionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = "$ACTIVITY_FIXTURE.FirstActivity"
    private val second = "$ACTIVITY_FIXTURE.SecondActivity"

    @Test
    fun realManifestIncludesPrivateDisabledAndAliasAndPackageUpdatesInvalidateSavedRules() = runBlocking {
        installActivityFixture()
        val entry = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
        val repo = entry.activityRepository()
        val scan = ActivityScanner(context).scanPackage(ACTIVITY_FIXTURE)!!
        assertEquals(5, scan.components.size)
        assertFalse(scan.components.single { it.className.endsWith("PrivateActivity") }.isExported)
        assertFalse(scan.components.single { it.className.endsWith("DisabledActivity") }.isEnabled)
        assertEquals(first, scan.components.single { it.className.endsWith("EntryAlias") }.canonicalName)
        entry.installedAppRepository().refreshPackage(ACTIVITY_FIXTURE)
        repo.refreshActivities(ACTIVITY_FIXTURE)
        repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.Offline, "Updated app")
        try {
            installActivityFixture(2)
            awaitActivityCondition("Package-change monitor did not invalidate the removed Activity") {
                runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().let { details ->
                    details.config?.versionCode == 2L && details.components.any { it.className == second && !it.isPresent }
                } }
            }
            assertFalse(repo.observeRuntimeRules().first().first { it.activityName == second }.isValid)
            assertEquals("Updated app", repo.observeDetails(ACTIVITY_FIXTURE).first().rules.first { it.activityName == second }.note)
        } finally {
            installActivityFixture()
            repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.FollowApp, "")
        }
    }

    @Test
    fun usageEventsConfirmSamePackageSwitchAndKeepOriginalResumeTimestamp() {
        installActivityFixture()
        val observations = ForegroundObservationStore()
        val detector = ForegroundAppDetector(context, DeclaredActivityResolver(context), observations)
        // Tests restore the untouched default mode; no other test grants this app-op permanently.
        activityShell("appops set ${context.packageName} GET_USAGE_STATS allow")
        try {
            assertTrue(detector.hasPermission())
            activityShell("am start -W -n $ACTIVITY_FIXTURE/.FirstActivity")
            var firstInfo: ForegroundAppInfo? = null
            awaitActivityCondition("First Activity was not confirmed by UsageEvents") {
                firstInfo = detector.detect(); firstInfo?.activityName == first && firstInfo?.activityConfirmed == true
            }
            detector.invalidateActivityConfirmation()
            assertFalse(detector.detect()!!.activityConfirmed)
            activityShell("am start -W -n $ACTIVITY_FIXTURE/.SecondActivity")
            var secondInfo: ForegroundAppInfo? = null
            awaitActivityCondition("Same-package Activity switch was not detected") {
                secondInfo = detector.detect(); secondInfo?.activityName == second && secondInfo?.activityConfirmed == true
            }
            assertEquals(firstInfo!!.packageName, secondInfo!!.packageName)
            assertTrue(secondInfo!!.eventTimestampMillis >= firstInfo!!.eventTimestampMillis)
            assertEquals(ForegroundInfoSource.UsageStats, secondInfo!!.source)
            assertEquals(secondInfo!!.eventTimestampMillis, detector.detect()!!.eventTimestampMillis)
            activityShell("appops set ${context.packageName} GET_USAGE_STATS deny")
            assertFalse(detector.detect()!!.activityConfirmed)
        } finally {
            activityShell("appops set ${context.packageName} GET_USAGE_STATS default")
            activityShell("am force-stop $ACTIVITY_FIXTURE")
        }
    }

    @Test
    fun realAccessibilityServiceConfirmsSwitchAndIgnoresNativeDialog() {
        installActivityFixture()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Default UiAutomation suppresses other accessibility services; explicitly allow ours.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val originalServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val originalEnabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        fun putSetting(key: String, value: String?) {
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try { assertTrue(Settings.Secure.putString(context.contentResolver, key, value)) }
            finally { automation.dropShellPermissionIdentity() }
        }
        val component = "${context.packageName}/${context.packageName}.runtime.SmartFlightAccessibilityService"
        val services = originalServices?.takeIf { it.isNotBlank() }?.let { "$it:$component" } ?: component
        val tracker = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java).foregroundTracker()
        try {
            putSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
            putSetting(Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            val configured = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            assertTrue(configured.any { it.flattenToString() == component })
            awaitActivityCondition("SmartFlight accessibility service did not connect") { tracker.isServiceConnected }
            shell("am start -W -n $ACTIVITY_FIXTURE/.FirstActivity")
            awaitActivityCondition("Platform accessibility event did not confirm FirstActivity") { tracker.latest()?.activityName == first }
            val buttons = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Show dialog").orEmpty()
            assertTrue("Native fixture dialog button was missing", buttons.isNotEmpty())
            assertTrue(buttons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitActivityCondition("Fixture dialog did not open") {
                automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Dialog is not an Activity")?.isNotEmpty() == true
            }
            assertEquals(first, tracker.latest()!!.activityName)
            shell("am start -W -n $ACTIVITY_FIXTURE/.SecondActivity")
            awaitActivityCondition("Platform accessibility did not confirm same-package SecondActivity") { tracker.latest()?.activityName == second }
            assertEquals(ForegroundInfoSource.Accessibility, tracker.latest()!!.source)
        } catch (error: Throwable) {
            shell("mkdir -p /sdcard/Download/smartflight-activities")
            for ((name, command) in listOf("accessibility-failure.txt" to "dumpsys accessibility", "package-failure.txt" to "dumpsys package ${context.packageName}")) {
                val file = File(checkNotNull(context.getExternalFilesDir(null)), name)
                file.writeText(shell(command))
                shell("cp ${file.absolutePath} /sdcard/Download/smartflight-activities/$name")
            }
            throw error
        } finally {
            putSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, originalServices)
            putSetting(Settings.Secure.ACCESSIBILITY_ENABLED, originalEnabled)
            shell("am force-stop $ACTIVITY_FIXTURE")
        }
    }

    @Test
    fun accessibilityRejectsDialogWidgetAndOldEventsButAcceptsDeclaredSamePackageActivity() {
        installActivityFixture()
        val tracker = AccessibilityForegroundAppTracker(context, DeclaredActivityResolver(context), ForegroundObservationStore())
        tracker.markServiceConnected()
        assertTrue(tracker.recordPackage(ACTIVITY_FIXTURE, 100, first)!!.activityChanged)
        assertNull(tracker.recordPackage(ACTIVITY_FIXTURE, 200, "android.app.Dialog"))
        assertNull(tracker.recordPackage(ACTIVITY_FIXTURE, 200, "android.widget.LinearLayout"))
        assertNull(tracker.recordPackage("com.android.systemui", 200, "android.widget.FrameLayout"))
        assertEquals(first, tracker.latest()!!.activityName)
        tracker.invalidateActivityConfirmation()
        assertNull(tracker.latest()!!.activityName)
        val change = tracker.recordPackage(ACTIVITY_FIXTURE, 300, second)!!
        assertFalse(change.packageChanged)
        assertTrue(change.activityChanged)
        assertNull(tracker.recordPackage(ACTIVITY_FIXTURE, 250, first))
        assertEquals(second, tracker.latest()!!.activityName)
        tracker.recordPackage(ACTIVITY_FIXTURE, 400, "$ACTIVITY_FIXTURE.EntryAlias")
        assertEquals(first, tracker.latest()!!.activityName)
    }
}
