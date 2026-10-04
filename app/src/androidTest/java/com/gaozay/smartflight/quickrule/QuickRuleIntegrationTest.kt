package com.gaozay.smartflight.quickrule

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.*
import com.gaozay.smartflight.apps.AppSyncTestEntryPoint
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuickRuleIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
    private val first = "$ACTIVITY_FIXTURE.FirstActivity"
    private val second = "$ACTIVITY_FIXTURE.SecondActivity"
    private fun text(id: Int) = context.getString(id)
    private fun intent(name: String? = null) = Intent(QuickRuleContract.ACTION).setPackage(context.packageName)
        .putExtra(QuickRuleContract.EXTRA_PACKAGE_NAME, ACTIVITY_FIXTURE).apply {
            name?.let { putExtra(QuickRuleContract.EXTRA_ACTIVITY_NAME, it) }
        }
    private fun waitForDialog() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.quick_rule_scope_app)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun save() {
        compose.onNodeWithText(text(R.string.activity_save)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.quick_rule_scope_app)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun publicActivityEntryPreservesNotesAppDefaultAndPausedChildren() = runBlocking {
        installActivityFixture()
        val repo = entry.activityRepository()
        val apps = entry.installedAppRepository()
        apps.refreshPackage(ACTIVITY_FIXTURE)
        apps.setManualOffline(ACTIVITY_FIXTURE)
        repo.refreshActivities(ACTIVITY_FIXTURE)
        repo.saveRule(ACTIVITY_FIXTURE, first, ActivityRuleMode.Online, "Keep my note")
        repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.Online, "Other page")
        repo.setRulesEnabled(ACTIVITY_FIXTURE, false)
        try {
            ActivityScenario.launch<QuickRuleActivity>(intent(".EntryAlias")).use { scenario ->
                waitForDialog()
                compose.onNodeWithText(first).assertExists()
                compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).performClick()
                compose.onNodeWithText(text(R.string.quick_rule_children_paused)).assertExists()
                compose.onNodeWithText(text(R.string.quick_rule_offline)).performClick()
                capture("quick-rule-activity-en.png")
                scenario.recreate()
                waitForDialog()
                compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).assertIsSelected()
                compose.onNodeWithText(text(R.string.quick_rule_offline)).assertIsSelected()
                save()
                awaitActivityCondition("Activity quick rule was not saved") {
                    runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().rules.any { it.activityName == first && it.mode == ActivityRuleMode.Offline.name } }
                }
            }
            val details = repo.observeDetails(ACTIVITY_FIXTURE).first()
            assertEquals("Keep my note", details.rules.first { it.activityName == first }.note)
            assertEquals(ActivityRuleMode.Online.name, details.rules.first { it.activityName == second }.mode)
            assertFalse(details.config!!.rulesEnabled)
            assertTrue(apps.getApp(ACTIVITY_FIXTURE)!!.isInBlacklist)
            ActivityScenario.launch<QuickRuleActivity>(intent(first)).use {
                waitForDialog()
                compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).performClick()
                compose.onNodeWithText(text(R.string.quick_rule_auto)).performClick()
                save()
                awaitActivityCondition("Activity auto did not restore inheritance") {
                    runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().rules.any { it.activityName == first && it.mode == ActivityRuleMode.FollowApp.name && it.note == "Keep my note" } }
                }
            }
            assertTrue(apps.getApp(ACTIVITY_FIXTURE)!!.isInBlacklist)
        } finally {
            repo.setRulesEnabled(ACTIVITY_FIXTURE, true)
            repo.saveRule(ACTIVITY_FIXTURE, first, ActivityRuleMode.FollowApp, "")
            repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.FollowApp, "")
            apps.resetToDefault(ACTIVITY_FIXTURE)
        }
    }

    @Test
    fun appChoicesCancelAndInvalidActivityDoNotDeleteChildRules() = runBlocking {
        installActivityFixture()
        val apps = entry.installedAppRepository()
        val repo = entry.activityRepository()
        apps.refreshPackage(ACTIVITY_FIXTURE)
        apps.resetToDefault(ACTIVITY_FIXTURE)
        repo.refreshActivities(ACTIVITY_FIXTURE)
        repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.Offline, "Keep child")
        try {
            ActivityScenario.launch<QuickRuleActivity>(intent("android.widget.FrameLayout")).use {
                waitForDialog()
                compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).assertIsNotEnabled()
                compose.onNodeWithText(text(R.string.quick_rule_online)).performClick()
                compose.onNodeWithText(text(R.string.cancel)).performClick()
            }
            assertFalse(apps.getApp(ACTIVITY_FIXTURE)!!.isInWhitelist)
            for (mode in QuickRuleMode.entries) {
                ActivityScenario.launch<QuickRuleActivity>(intent()).use {
                    waitForDialog()
                    compose.onNodeWithText(text(mode.labelRes)).performClick()
                    save()
                    awaitActivityCondition("App quick rule $mode was not saved") { runBlocking { QuickRuleMode.forApp(apps.getApp(ACTIVITY_FIXTURE)!!) == mode } }
                }
            }
            val app = apps.getApp(ACTIVITY_FIXTURE)!!
            assertFalse(app.isInBlacklist)
            assertFalse(app.isInWhitelist)
            assertEquals(app.isAutoDetectedOnline, app.isInOnlineList)
            assertEquals("Keep child", repo.observeDetails(ACTIVITY_FIXTURE).first().rules.first { it.activityName == second }.note)
            assertEquals(ActivityRuleMode.Offline.name, repo.observeDetails(ACTIVITY_FIXTURE).first().rules.first { it.activityName == second }.mode)
            ActivityScenario.launch<QuickRuleActivity>(Intent(QuickRuleContract.ACTION).setPackage(context.packageName)
                .putExtra(QuickRuleContract.EXTRA_ACTIVITY_NAME, second)).use {
                compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.quick_rule_invalid_request)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(text(R.string.activity_save)).assertIsNotEnabled()
                compose.onNodeWithText(text(R.string.cancel)).performClick()
            }
        } finally {
            repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.FollowApp, "")
            apps.resetToDefault(ACTIVITY_FIXTURE)
        }
    }

    @Test
    fun realSystemTileCapturesUnderlyingActivityAndPublicNoExtrasEntryDoesToo() = runBlocking {
        installActivityFixture()
        val settings = entry.settingsRepository()
        val original = settings.settings.first()
        val originalTiles = activityShell("settings get secure sysui_qs_tiles").trim()
        val originalUsage = activityShell("cmd appops get ${context.packageName} GET_USAGE_STATS")
        val component = "${context.packageName}/.quickrule.QuickRuleTileService"
        val apps = entry.installedAppRepository()
        val repo = entry.activityRepository()
        try {
            settings.updateSettings { it.copy(foregroundMonitorMode = ForegroundMonitorMode.UsageStats) }
            activityShell("appops set ${context.packageName} GET_USAGE_STATS allow")
            apps.refreshPackage(ACTIVITY_FIXTURE)
            apps.resetToDefault(ACTIVITY_FIXTURE)
            repo.refreshActivities(ACTIVITY_FIXTURE)
            activityShell("am start -W -n $ACTIVITY_FIXTURE/.SecondActivity")
            awaitActivityCondition("Underlying fixture was not detected") { runBlocking { entry.quickRuleTargets().capture()?.activityName == second } }
            activityShell("cmd statusbar add-tile $component")
            activityShell("settings put secure sysui_qs_tiles custom($component)")
            activityShell("cmd statusbar expand-settings")
            awaitActivityCondition("Quick rule tile did not appear") {
                instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                    try { root.findAccessibilityNodeInfosByText(text(R.string.quick_rule_tile_label)).isNotEmpty() }
                    finally { root.recycle() }
                } == true
            }
            activityShell("cmd statusbar click-tile $component")
            waitForDialog()
            compose.onNodeWithText(second).assertExists()
            compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).assertIsEnabled()
            val standaloneTracker = com.gaozay.smartflight.runtime.AccessibilityForegroundAppTracker(context,
                DeclaredActivityResolver(context), com.gaozay.smartflight.runtime.ForegroundObservationStore())
            val now = System.currentTimeMillis()
            standaloneTracker.recordPackage(ACTIVITY_FIXTURE, now, second)
            assertNull(standaloneTracker.recordPackage(context.packageName, now + 1, QuickRuleContract.ACTIVITY_CLASS))
            assertNull(standaloneTracker.recordPackage(context.packageName, now + 2, "android.app.Dialog"))
            assertEquals(second, standaloneTracker.latest()?.activityName)
            compose.onNodeWithText(text(R.string.quick_rule_online)).performClick()
            capture("quick-rule-tile-en.png")
            save()
            awaitActivityCondition("Tile app rule not saved") { runBlocking { apps.getApp(ACTIVITY_FIXTURE)?.isInWhitelist == true } }
            // Simulate an automation app issuing only the public action while the fixture is visible.
            activityShell("am start -W -a ${QuickRuleContract.ACTION} -p ${context.packageName}")
            waitForDialog()
            compose.onNodeWithText(second).assertExists()
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            // Both real foreground paths must ignore this host-owned transparent editor.
            val detector = entry.foregroundDetector()
            assertEquals(ACTIVITY_FIXTURE, detector.detect()?.packageName)
        } finally {
            activityShell("cmd statusbar collapse")
            activityShell("cmd statusbar remove-tile $component")
            if (originalTiles == "null") activityShell("settings delete secure sysui_qs_tiles")
            else activityShell("settings put secure sysui_qs_tiles $originalTiles")
            activityShell("appops set ${context.packageName} GET_USAGE_STATS ${if (originalUsage.contains("allow")) "allow" else "default"}")
            settings.updateSettings { original }
            apps.resetToDefault(ACTIVITY_FIXTURE)
            activityShell("am force-stop $ACTIVITY_FIXTURE")
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(checkNotNull(context.getExternalFilesDir(null)), name)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        activityShell("mkdir -p /sdcard/Download/smartflight-activities")
        activityShell("cp ${file.absolutePath} /sdcard/Download/smartflight-activities/$name")
    }
}
