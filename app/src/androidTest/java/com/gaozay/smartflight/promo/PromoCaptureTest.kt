package com.gaozay.smartflight.promo

import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.LocaleList
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.ActivityRuleMode
import com.gaozay.smartflight.apps.AppSyncTestEntryPoint
import com.gaozay.smartflight.domain.model.*
import com.gaozay.smartflight.settings.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Video acquisition: real MainActivity, ViewModel, repositories, Shizuku and Android services. */
@RunWith(AndroidJUnit4::class)
class PromoCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val entry get() = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
    private val fixture = "com.gaozay.smartflight.activityfixture"
    private val first = "$fixture.FirstActivity"
    private val second = "$fixture.SecondActivity"
    private val out get() = File(context.getExternalFilesDir(null), "promo").also { it.mkdirs() }
    private var clip = ""
    private var clipStarted = 0L
    private fun text(id: Int) = context.getString(id)
    private fun shell(command: String): String = inst.uiAutomation.executeShellCommand(command).use { fd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
    }
    private fun hold(ms: Long = 1000) { SystemClock.sleep(ms) }
    private fun exists(s: String) = compose.onAllNodesWithText(s).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    private fun scroll(s: String): SemanticsNodeInteraction {
        val match = hasText(s) and !hasSetTextAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(match)
        return compose.onNode(match).performScrollTo()
    }
    private fun click(id: Int) { scroll(text(id)).performTouchInput { click() }; hold(650) }
    private fun back() { compose.onNodeWithContentDescription(text(R.string.back)).performTouchInput { click() }; hold(700) }
    private fun main() { shell("am start -W -f 0x24000000 -n ${context.packageName}/.MainActivity"); hold(1500) }
    private fun dashboard() {
        main()
        repeat(5) {
            if (compose.onAllNodesWithContentDescription(text(R.string.back)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) back()
            else { compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0); return }
        }
    }
    private fun nativeClick(vararg names: String): Boolean {
        val root = inst.uiAutomation.rootInActiveWindow ?: return false
        for (name in names) {
            for (node in root.findAccessibilityNodeInfosByText(name)) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                if (node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0) {
                    shell("input tap ${bounds.centerX()} ${bounds.centerY()}"); hold(900); return true
                }
            }
        }
        return false
    }
    private fun startClip(name: String) {
        clip = name; clipStarted = SystemClock.elapsedRealtime()
        shell("mkdir -p /sdcard/Download/smartflight-promo")
        File(out, "active-clip").writeText(name)
        shell("cp ${out.absolutePath}/active-clip /sdcard/Download/smartflight-promo/active-clip")
        hold(900); event("start")
    }
    private fun stopClip() {
        event("stop"); capture(clip)
        File(out, "active-clip").writeText("")
        shell("cp ${out.absolutePath}/active-clip /sdcard/Download/smartflight-promo/active-clip"); shell("pkill -2 screenrecord"); hold(1500)
    }
    private fun event(name: String) {
        val data = shell("settings get global mobile_data").trim()
        File(out, "events.jsonl").appendText("{\"clip\":\"$clip\",\"timeMs\":${SystemClock.elapsedRealtime()-clipStarted},\"event\":\"$name\",\"mobileData\":\"$data\"}\n")
        shell("cp ${out.absolutePath}/events.jsonl /sdcard/Download/smartflight-promo/events.jsonl")
    }
    private fun capture(name: String) {
        val bitmap = checkNotNull(inst.uiAutomation.takeScreenshot())
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); shell("cp ${out.absolutePath}/$name.png /sdcard/Download/smartflight-promo/$name.png")
    }
    private fun awaitData(on: Boolean) {
        val expected = if (on) "1" else "0"
        val until = SystemClock.elapsedRealtime()+25000
        while (SystemClock.elapsedRealtime() < until) {
            if (shell("settings get global mobile_data").trim() == expected) { event(if (on) "data-on" else "data-off"); hold(1200); return }
            hold(200)
        }
        fail("Actual mobile data did not become $expected")
    }

    @Test fun capturePromotionalOperations() = runBlocking<Unit> {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-CN")
        val settings = entry.settingsRepository()
        settings.updateSettings { it.copy(automationEnabled=false, networkControlMode=NetworkControlMode.MobileData,
            preferredExecutorType=ExecutorType.Shizuku, foregroundMonitorMode=ForegroundMonitorMode.UsageStats,
            pauseAutomationOnExternalNetworkChange=false, skipReconnectOnWifi=false, skipDisconnectOnWifi=false,
            appExitDelaySeconds=5, screenOffDelaySeconds=5, screenOffDisconnectEnabled=false,
            themeMode=ThemeMode.Light, themePalette=ThemePalette.WarmPaper) }
        ActivityScenario.launch(MainActivity::class.java).use {
          try {
            hold(2500)
            startClip("access")
            click(R.string.request_shizuku_permission)
            var allowed = false
            repeat(20) { if (!allowed) { allowed = nativeClick("Allow all the time", "始终允许", "总是允许"); if (!allowed) hold(300) } }
            assertTrue("Shizuku permission dialog was not accepted", allowed)
            hold(1800)
            click(R.string.try_automatic_permission_grants)
            hold(2000)
            for (label in listOf("关闭", "完成", "确定", "知道了")) if (exists(label)) compose.onAllNodesWithText(label).onLast().performTouchInput { click() }
            compose.waitUntil(20000) { entry.accessRepository().accessGateState.value.canEnterApp }
            scroll(text(R.string.app_scope))
            hold(1400); stopClip()

            val apps = entry.installedAppRepository()
            apps.refreshPackage(fixture); apps.setManualOnline(fixture)
            apps.refreshPackage(context.packageName); apps.setManualOffline(context.packageName)
            for (pkg in listOf("com.google.android.apps.nexuslauncher", "com.android.launcher3")) {
                if (apps.getApp(pkg) != null) apps.setManualOffline(pkg)
            }
            val repo = entry.activityRepository(); repo.refreshActivities(fixture)
            settings.updateSettings { s -> s.withAutomationEnabled() }
            hold(2500)

            startClip("rules")
            click(R.string.automation_rules)
            scroll(text(R.string.connectivity_control_method)); hold(1300)
            click(R.string.app_exit_delay_in_seconds)
            compose.onAllNodesWithText("+").onFirst().performScrollTo().performTouchInput { click() }; hold(1400)
            compose.onAllNodesWithText("-").onFirst().performTouchInput { click() }; hold(1200)
            scroll(text(R.string.disconnect_automatically_when_the_screen_turns_off)); hold(1300)
            scroll(text(R.string.do_not_disconnect_automatically_while_connected_to_wi_fi)); hold(1400)
            stopClip(); back()

            // Only initial preparation writes mobile data. Clip actions are handled by the app.
            shell("svc data disable"); shell("input keyevent KEYCODE_HOME"); hold(1500)
            startClip("app-auto")
            shell("am start -W -n $fixture/.FirstActivity")
            awaitData(true); hold(1400)
            event("leave-app"); shell("input keyevent KEYCODE_HOME")
            hold(5100); awaitData(false)
            main(); click(R.string.diagnostics_and_logs); scroll(text(R.string.mobile_data_state)); hold(1700)
            stopClip()

            settings.updateSettings { s -> s.copy(appExitDisconnectEnabled=false, screenOffDisconnectEnabled=true) }
            dashboard(); shell("am start -W -n $fixture/.FirstActivity"); awaitData(true)
            startClip("screen-off")
            hold(1600); event("screen-off"); shell("input keyevent KEYCODE_SLEEP")
            hold(5600); awaitData(false); event("off-while-asleep")
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard"); hold(1500)
            main(); click(R.string.diagnostics_and_logs); scroll(text(R.string.recent_logs)); hold(2200)
            stopClip(); dashboard()
            settings.updateSettings { s -> s.copy(appExitDisconnectEnabled=true, screenOffDisconnectEnabled=false) }

            startClip("activity-editor")
            click(R.string.app_scope)
            val search = compose.onNodeWithText(text(R.string.search_app_name_or_package_name)).performScrollTo()
            search.performTouchInput { click() }; search.performTextInput("联网演示"); hold(1100); shell("input keyevent KEYCODE_BACK"); hold(700)
            scroll("联网演示").performTouchInput { click() }; hold(1700)
            scroll(second).performTouchInput { click() }; hold(900)
            click(R.string.offline); click(R.string.activity_save); hold(1500)
            scroll(second).performTouchInput { click() }; hold(800)
            scroll(text(R.string.activity_follow_app)); hold(2200)
            stopClip(); back()
            // Real repository result of the filmed save, plus the independently configured online component.
            assertEquals(ActivityRuleMode.Offline.name, repo.observeDetails(fixture).first().rules.first { r -> r.activityName==second }.mode)
            repo.saveRule(fixture, first, ActivityRuleMode.Online, "在线功能")
            repo.setRulesEnabled(fixture,true)
            shell("svc data disable"); shell("input keyevent KEYCODE_HOME"); hold(900)
            startClip("activity-switch")
            shell("am start -W -n $fixture/.FirstActivity"); awaitData(true)
            assertTrue(nativeClick("进入离线功能")); awaitData(false); hold(1400)
            stopClip()

            shell("am start -W -n $fixture/.FirstActivity"); hold(1800)
            val component="${context.packageName}/.quickrule.QuickRuleTileService"
            shell("cmd statusbar add-tile $component")
            shell("settings put secure sysui_qs_tiles custom($component)")
            startClip("quick-rule")
            shell("cmd statusbar expand-settings"); hold(1600)
            shell("cmd statusbar click-tile $component")
            compose.waitUntil(15000) { exists(text(R.string.quick_rule_scope_app)) }
            compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).performScrollTo().performTouchInput { click() }; hold(900)
            compose.onNodeWithText(text(R.string.quick_rule_auto)).performScrollTo().performTouchInput { click() }; hold(1200)
            compose.onNodeWithText(text(R.string.activity_save)).performTouchInput { click() }; hold(1800)
            stopClip()

            shell("cmd statusbar collapse"); shell("am start -W -n $fixture/.FirstActivity"); hold(1200)
            startClip("third-party")
            assertTrue(nativeClick("快捷声明入口"))
            compose.waitUntil(15000) { exists(text(R.string.quick_rule_scope_app)) }
            hold(1800)
            compose.onNodeWithText(text(R.string.quick_rule_scope_app)).performScrollTo().performTouchInput { click() }
            compose.onNodeWithText(text(R.string.quick_rule_online)).performScrollTo().performTouchInput { click() }; hold(900)
            compose.onNodeWithText(text(R.string.activity_save)).performTouchInput { click() }; hold(1100)
            stopClip()

            dashboard(); startClip("apps")
            click(R.string.app_scope)
            val input=compose.onNodeWithText(text(R.string.search_app_name_or_package_name)).performScrollTo()
            input.performTextClearance(); input.performTextInput("联网演示"); hold(1000); shell("input keyevent KEYCODE_BACK"); hold(700)
            scroll("联网演示"); hold(1700)
            compose.onNodeWithContentDescription(text(R.string.change_rule)).performTouchInput { click() }; hold(600)
            compose.onNodeWithText(text(R.string.set_as_online)).performTouchInput { click() }; hold(1700)
            stopClip(); dashboard()

            startClip("diagnostics")
            click(R.string.diagnostics_and_logs)
            scroll(text(R.string.mobile_data_state)); hold(1700)
            click(R.string.advanced_actions)
            click(R.string.probe_current_control_state)
            compose.onAllNodesWithText(text(R.string.probe_current_control_state)).onLast().performTouchInput { click() }; hold(1500)
            scroll(text(R.string.recent_logs)); hold(2200)
            stopClip(); dashboard()

            startClip("appearance")
            click(R.string.appearance); click(R.string.dark); hold(1500)
            click(R.string.night_flight); hold(1000)
            scroll(text(R.string.corner_style)); hold(1300)
            stopClip()
            settings.updateSettings { s -> s.copy(themeMode=ThemeMode.Light, themePalette=ThemePalette.WarmPaper) }
            dashboard(); startClip("dashboard")
            hold(1800)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0); hold(900)
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }; hold(1800)
            assertTrue(exists(text(R.string.automation_paused)))
            stopClip()
          } catch (failure: Throwable) { capture("failure"); throw failure }
        }
    }
}
