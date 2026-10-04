package com.gaozay.smartflight.screen

import android.graphics.Bitmap
import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.SmartFlightUiState
import com.gaozay.smartflight.activities.*
import com.gaozay.smartflight.apps.*
import com.gaozay.smartflight.permission.*
import com.gaozay.smartflight.ui.*
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import com.gaozay.smartflight.update.UpdateUiState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Runs inside a real 410x502 emulator window, including status/navigation bars. */
@RunWith(AndroidJUnit4::class)
class SmallScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
    private fun text(id: Int) = context.getString(id)
    private val apps = AppsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val system = SystemIntentActions({}, {}, {}, {})

    @Before fun configureLocale() {
        val arguments = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(arguments.containsKey("screenCase"))
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(arguments.getString("screenLocale", "en"))
        compose.waitForIdle()
    }
    @After fun captureFinalWindow() { capture("final-window") }

    @Test fun setupCanScrollToLastAction() {
        // MainActivity's real cold-start access gate (no root on the emulator).
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text(R.string.smartflight_setup_check)).fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo(R.string.continue_checking).assertIsDisplayed().performClick()
        capture("setup-bottom")
    }

    @Test fun dashboardSettingsDiagnosticsAppearanceAndAboutRemainReachable() = runBlocking<Unit> {
        installActivityFixture()
        entry.installedAppRepository().refreshPackage(ACTIVITY_FIXTURE)
        val app = checkNotNull(entry.installedAppRepository().getApp(ACTIVITY_FIXTURE))
        val initial = AccessGateState()
        // Inject a ready gate to inspect post-setup UI without pretending the emulator has root.
        val ready = initial.copy(
            usageStatsAccess = initial.usageStatsAccess.copy(status = AccessCheckStatus.Granted, satisfiesRequirement = true),
            advancedAccess = AdvancedAccessState(checks = listOf(initial.usageStatsAccess.copy(satisfiesRequirement = true))),
        )
        val state = mutableStateOf(SmartFlightUiState(accessGateState = ready))
        var updates = 0
        val actions = SmartFlightActions(
            SettingsActions({ transform -> state.value = state.value.copy(settings = transform(state.value.settings)); updates++ }, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            AutomationActions({}, {}), apps, AccessActions({}, {}, {}, {}, {}),
            DiagnosticsActions({}, {}, {}, {}, {}, {}), system, UpdateActions({}, {}, {}, {}, {}),
        )
        compose.runOnUiThread { compose.activity.setContent { SmartFlightTheme {
            SmartFlightRoot(state.value, AppsUiState(apps = listOf(app), totalCount = 1, filteredCount = 1), UpdateUiState.Idle, actions)
        } } }
        capture("dashboard")
        scrollTo(R.string.app_scope).performClick()
        scrollTo(app.label).assertIsDisplayed()
        capture("apps")
        back()
        scrollTo(R.string.automation_rules).performClick()
        scrollTo(R.string.app_exit_delay_in_seconds).assertIsDisplayed()
        // The whole delay label and both buttons must retain nonzero, in-window bounds.
        val label = compose.onNodeWithText(text(R.string.app_exit_delay_in_seconds)).fetchSemanticsNode()
        assertTrue("Delay label lost its width", label.boundsInRoot.width > 1f)
        compose.onAllNodesWithText("+").onFirst().assertIsDisplayed().performClick()
        assertEquals(1, updates)
        capture("settings-delay")
        scrollTo(R.string.disconnect_prompt_text).assertIsDisplayed()
        capture("settings-bottom")
        back()
        scrollTo(R.string.diagnostics_and_logs).performClick()
        scrollTo(R.string.advanced_actions).performClick()
        scrollTo(R.string.clear_logs).assertIsDisplayed()
        capture("diagnostics-bottom")
        back()
        scrollTo(R.string.appearance).performClick()
        scrollTo(R.string.corner_style).assertIsDisplayed()
        capture("appearance-bottom")
        back()
        scrollTo(R.string.about).performClick()
        scrollTo(R.string.check_for_updates).assertIsDisplayed()
        capture("about-update")
        scrollTo(R.string.opens_the_release_page_only).assertIsDisplayed()
        capture("about-bottom")
    }

    @Test fun activityEditorCanScrollChangeModeAndSave() = runBlocking<Unit> {
        installActivityFixture()
        entry.installedAppRepository().refreshPackage(ACTIVITY_FIXTURE)
        val repo = entry.activityRepository()
        repo.refreshActivities(ACTIVITY_FIXTURE)
        val details = repo.observeDetails(ACTIVITY_FIXTURE).first()
        val app = entry.installedAppRepository().getApp(ACTIVITY_FIXTURE)
        val second = "$ACTIVITY_FIXTURE.SecondActivity"
        var saved: ActivityRuleMode? = null
        compose.runOnUiThread { compose.activity.setContent { SmartFlightTheme {
            ActivityManagementScreen(ActivityManagementState(ACTIVITY_FIXTURE, app, details),
                ActivityActions(save = { _, mode, _ -> saved = mode }), system, apps)
        } } }
        scrollTo(R.string.activity_record_title).assertIsDisplayed()
        capture("activity-details")
        scrollTo(second).performClick()
        scrollTo(R.string.online).performClick()
        scrollTo(R.string.activity_save).assertIsDisplayed()
        capture("activity-editor-save")
        compose.onNodeWithText(text(R.string.activity_save)).performClick()
        assertEquals(ActivityRuleMode.Online, saved)
    }

    private fun back() { compose.onNodeWithContentDescription(text(R.string.back)).performClick() }
    private fun scrollTo(id: Int) = scrollTo(text(id))
    private fun scrollTo(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        assertEquals(410, bitmap.width)
        assertEquals(502, bitmap.height)
        val arguments = InstrumentationRegistry.getArguments()
        val prefix = arguments.getString("screenCase", "unknown")
        val file = File(checkNotNull(context.getExternalFilesDir(null)), "$prefix-$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        activityShell("mkdir -p /sdcard/Download/smartflight-small-screen")
        activityShell("cp ${file.absolutePath} /sdcard/Download/smartflight-small-screen/${file.name}")
    }
}
