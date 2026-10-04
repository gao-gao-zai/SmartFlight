package com.gaozay.smartflight.activities

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.AppSyncTestEntryPoint
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.ui.*
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
    private val second = "$ACTIVITY_FIXTURE.SecondActivity"
    private val apps = AppsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val system = SystemIntentActions({}, {}, {}, {})
    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun recordRealSwitchThenEditSaveRestoreAndKeepNotesWhenFollowingApp() = runBlocking {
        installActivityFixture()
        activityShell("appops set ${context.packageName} GET_USAGE_STATS allow")
        val settings = entry.settingsRepository()
        val originalSettings = settings.settings.first()
        val repo = entry.activityRepository()
        try {
            settings.updateSettings { it.copy(foregroundMonitorMode = ForegroundMonitorMode.UsageStats) }
            entry.accessRepository().refresh()
            entry.installedAppRepository().refreshPackage(ACTIVITY_FIXTURE)
            repo.refreshActivities(ACTIVITY_FIXTURE)
            repo.setRulesEnabled(ACTIVITY_FIXTURE, true)
            repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.FollowApp, "")
            lateinit var vm: ActivityManagementViewModel
            compose.runOnUiThread {
                vm = ViewModelProvider(compose.activity)[ActivityManagementViewModel::class.java]
                vm.selectApp(ACTIVITY_FIXTURE)
                compose.activity.setContent {
                    val state by vm.state.collectAsState()
                    SmartFlightTheme { ActivityManagementScreen(state, actions(vm), system, apps) }
                }
            }
            awaitActivityCondition("App details did not finish loading") { vm.state.value.app != null && vm.state.value.canRecord && !vm.state.value.isScanning }
            scrollTo(text(R.string.activity_record_title)).performClick()
            scrollTo(text(R.string.activity_record_start)).performClick()
            awaitActivityCondition("Recording did not start") { entry.activityRecorder().state.value.active }
            awaitActivityCondition("Launched Activity was not recorded") {
                runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().visits.any { it.activityName.endsWith("FirstActivity") && it.sessionId == entry.activityRecorder().state.value.sessionId } }
            }
            activityShell("am start -W -n $ACTIVITY_FIXTURE/.SecondActivity")
            awaitActivityCondition("Second Activity was not recorded") {
                runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().visits.any { it.activityName == second && it.sessionId == entry.activityRecorder().state.value.sessionId } }
            }
            activityShell("am start -W -f 0x34000000 -n ${context.packageName}/.MainActivity")
            awaitActivityCondition("Returning to SmartFlight did not stop recording") { !entry.activityRecorder().state.value.active }
            val session = entry.activityRecorder().state.value.sessionId
            val visits = repo.observeDetails(ACTIVITY_FIXTURE).first().visits.filter { it.sessionId == session }
            assertEquals(2, visits.size)
            assertEquals(visits.sortedBy { it.firstEnteredAtMillis }, visits)
            assertTrue(repo.observeRuntimeRules().first().none { it.packageName == ACTIVITY_FIXTURE })
            scrollTo(second).assertIsDisplayed()
            capture("record-en.png")
            compose.onNodeWithText(second).performClick()
            scrollTo(text(R.string.online)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("Payment")
            scrollTo(text(R.string.activity_save)).performClick()
            awaitActivityCondition("Editor did not save the override") {
                runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().rules.any { it.activityName == second && it.mode == ActivityRuleMode.Online.name && it.note == "Payment" } }
            }
            scrollTo(second).performClick()
            compose.onNodeWithText("Payment").assertExists()
            capture("editor-en.png")
            scrollTo(text(R.string.activity_follow_app)).performClick()
            scrollTo(text(R.string.activity_save)).performClick()
            awaitActivityCondition("Following the app did not retain the note") {
                runBlocking { repo.observeDetails(ACTIVITY_FIXTURE).first().rules.any { it.activityName == second && it.mode == ActivityRuleMode.FollowApp.name && it.note == "Payment" } }
            }
            scrollTo(second).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("Unsaved")
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            compose.onNodeWithText(text(R.string.activity_unsaved_title)).assertExists()
            compose.onNodeWithText(text(R.string.activity_discard)).performClick()
            assertEquals("Payment", repo.observeDetails(ACTIVITY_FIXTURE).first().rules.first { it.activityName == second }.note)
        } finally {
            entry.activityRecorder().stop()
            settings.updateSettings { originalSettings }
            activityShell("appops set ${context.packageName} GET_USAGE_STATS default")
            entry.accessRepository().refresh()
            activityShell("am force-stop $ACTIVITY_FIXTURE")
        }
    }

    @Test
    fun detailsAndEditorRemainScrollableInChineseAndOnNarrowLargeFontDisplay() = runBlocking {
        installActivityFixture()
        entry.installedAppRepository().refreshPackage(ACTIVITY_FIXTURE)
        val repo = entry.activityRepository()
        repo.refreshActivities(ACTIVITY_FIXTURE)
        repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.Online, "Payment")
        val details = repo.observeDetails(ACTIVITY_FIXTURE).first()
        val app = entry.installedAppRepository().getApp(ACTIVITY_FIXTURE)
        val locale = mutableStateOf("zh")
        val narrow = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                val base = LocalContext.current
                val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locale.value)) }
                val localized = base.createConfigurationContext(config)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalContext provides localized, LocalDensity provides Density(density.density, if (narrow.value) 1.5f else 1f)) {
                    SmartFlightTheme {
                        Box(if (narrow.value) Modifier.width(240.dp) else Modifier) {
                            ActivityManagementScreen(ActivityManagementState(ACTIVITY_FIXTURE, app, details), ActivityActions(), system, apps)
                        }
                    }
                }
            }
        }
        val zh = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("zh")) })
        compose.onNodeWithText(zh.getString(R.string.activity_details_title)).assertExists()
        capture("details-zh.png")
        scrollTo(second).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Payment")
        scrollTo(zh.getString(R.string.activity_save)).assertIsDisplayed()
        capture("editor-zh.png")
        compose.onNodeWithContentDescription(zh.getString(R.string.back)).performClick()
        compose.runOnUiThread { locale.value = "en"; narrow.value = true }
        val en = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("en")) })
        compose.onNodeWithText(en.getString(R.string.activity_details_title)).assertExists()
        scrollTo(second).performClick()
        scrollTo(en.getString(R.string.activity_save)).assertIsDisplayed()
        capture("editor-narrow-large-font-en.png")
        repo.saveRule(ACTIVITY_FIXTURE, second, ActivityRuleMode.FollowApp, "")
    }

    private fun actions(vm: ActivityManagementViewModel) = ActivityActions(vm::selectApp, vm::refresh, vm::save, vm::setEnabled,
        vm::acknowledgeReview, vm::startRecording, vm::stopRecording, vm::refreshIdentification)
    private fun scrollTo(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(checkNotNull(context.getExternalFilesDir(null)), name)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        activityShell("mkdir -p /sdcard/Download/smartflight-activities")
        activityShell("cp ${file.absolutePath} /sdcard/Download/smartflight-activities/$name")
    }
}
