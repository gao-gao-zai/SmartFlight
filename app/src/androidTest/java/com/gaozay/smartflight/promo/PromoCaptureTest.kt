package com.gaozay.smartflight.promo

import android.app.LocaleManager
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
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
    private val weather = "org.breezyweather"
    private val shellLock = Any()
    @Volatile private var sampling = true
    private val out get() = File(context.getExternalFilesDir(null), "promo").also { it.mkdirs() }
    @Volatile private var clip = ""
    @Volatile private var clipStarted = 0L
    private fun text(id: Int) = context.getString(id)
    private fun shell(command: String): String = synchronized(shellLock) {
        inst.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
        }
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
                    var clickable = node
                    while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
                    if (!clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        hold(500); clickable.getBoundsInScreen(bounds)
                        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
                    }
                    hold(900); return true
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
        event("stop"); runCatching { capture(clip) }
        clip = ""
        File(out, "active-clip").writeText("")
        shell("cp ${out.absolutePath}/active-clip /sdcard/Download/smartflight-promo/active-clip"); hold(2000)
    }
    private fun event(name: String) {
        if (clip.isEmpty()) return
        val data = shell("settings get global mobile_data").trim()
        synchronized(out.absolutePath.intern()) {
            File(out, "events.jsonl").appendText("{\"clip\":\"$clip\",\"timeMs\":${SystemClock.elapsedRealtime()-clipStarted},\"event\":\"$name\",\"mobileData\":\"$data\"}\n")
        }
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

    private fun allNativeText(): String {
        val root = inst.uiAutomation.rootInActiveWindow ?: return ""
        fun collect(node: AccessibilityNodeInfo): String = buildString {
            append(node.text ?: ""); append(' '); append(node.contentDescription ?: ""); append('\n')
            for (i in 0 until node.childCount) node.getChild(i)?.let { append(collect(it)) }
        }
        return collect(root)
    }
    private fun launchWeather() { shell("am start -W -n $weather/.ui.main.MainActivity"); hold(800) }
    private fun openAppDetails(name: String) {
        dashboard(); click(R.string.app_scope)
        val input = compose.onNodeWithText(text(R.string.search_app_name_or_package_name)).performScrollTo()
        input.performTextClearance(); input.performTextInput(name)
        shell("input keyevent KEYCODE_BACK"); hold(900)
        scroll(name).performTouchInput { click() }; hold(1400)
    }
    private fun prepareWeather() {
        shell("svc data enable")
        shell("am start -W -a android.intent.action.VIEW -d geo:59.9139,10.7522 -n $weather/.ui.main.MainActivity")
        hold(4500)
        repeat(18) {
            nativeClick("完成", "Done", "稍后", "Later", "取消", "Cancel")
            hold(700)
        }
        nativeClick("奥斯陆", "Oslo")
        val until = SystemClock.elapsedRealtime()+45000
        while (SystemClock.elapsedRealtime() < until && !allNativeText().contains("°") && !allNativeText().contains("℃")) hold(1000)
        File(out, "weather-ui.txt").writeText(allNativeText())
        shell("cp ${out.absolutePath}/weather-ui.txt /sdcard/Download/smartflight-promo/weather-ui.txt")
        capture("weather-prepared")
        assertTrue("Weather app did not display genuine forecast data", allNativeText().contains("°") || allNativeText().contains("℃"))
        shell("input keyevent KEYCODE_HOME"); hold(1200)
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
            repeat(30) { if (!allowed) {
                nativeClick("Allow all the time", "始终允许", "总是允许")
                allowed = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
                if (!allowed) hold(500)
            } }
            assertTrue("Shizuku permission dialog was not accepted", allowed)
            hold(1200)
            entry.accessRepository().refresh()
            assertTrue("Real advanced access did not become available", entry.accessRepository().accessGateState.value.advancedAccess.isAvailable)
            click(R.string.try_automatic_permission_grants)
            hold(2000)
            for (label in listOf("关闭", "完成", "确定", "知道了")) if (exists(label)) compose.onAllNodesWithText(label).onLast().performTouchInput { click() }
            compose.waitUntil(20000) { entry.accessRepository().accessGateState.value.canEnterApp }
            scroll(text(R.string.app_scope))
            hold(1400); stopClip()

            prepareWeather()
            val apps = entry.installedAppRepository()
            apps.refreshPackage(fixture); apps.setManualOnline(fixture)
            apps.refreshPackage(weather); apps.setManualOffline(weather)
            apps.refreshPackage(context.packageName); apps.setManualOffline(context.packageName)
            for (pkg in listOf("com.google.android.apps.nexuslauncher", "com.android.launcher3", "com.android.systemui", "com.android.settings")) {
                if (apps.getApp(pkg) != null) apps.setManualOffline(pkg)
            }
            val repo = entry.activityRepository(); repo.refreshActivities(fixture)
            repo.saveRule(fixture, first, ActivityRuleMode.Online, "在线功能")
            repo.saveRule(fixture, second, ActivityRuleMode.FollowApp, "离线功能")
            repo.setRulesEnabled(fixture, true)
            settings.updateSettings { s -> s.withAutomationEnabled() }
            hold(2200)
            val sampler = Thread {
                while (sampling) {
                    if (clip.isNotEmpty()) runCatching { event("sample") }
                    hold(250)
                }
            }.also { it.start() }

            openAppDetails(checkNotNull(apps.getApp(weather)).label)
            scroll(text(R.string.online)); hold(1100)
            startClip("weather-rule")
            click(R.string.online); hold(2600); event("rule-saved")
            assertTrue(apps.getApp(weather)!!.isInWhitelist)
            stopClip()

            shell("svc data disable"); shell("input keyevent KEYCODE_HOME"); hold(1500)
            startClip("weather-auto")
            hold(1700); event("open-weather"); launchWeather()
            awaitData(true); hold(4000)
            event("leave-app"); shell("input keyevent KEYCODE_HOME")
            hold(5400); awaitData(false); hold(2300)
            stopClip()

            dashboard(); click(R.string.automation_rules)
            scroll(text(R.string.disconnect_automatically_when_the_screen_turns_off)); hold(1000)
            startClip("screen-rule")
            compose.onNodeWithContentDescription(text(R.string.disconnect_automatically_when_the_screen_turns_off)).performTouchInput { click() }
            hold(2300)
            scroll(text(R.string.screen_off_delay_in_seconds)); hold(2600)
            stopClip()
            settings.updateSettings { s -> s.copy(appExitDisconnectEnabled=false, screenOffDisconnectEnabled=true) }
            launchWeather(); awaitData(true)
            startClip("screen-sleep")
            hold(2000); event("screen-off"); shell("input keyevent KEYCODE_SLEEP")
            hold(5700); awaitData(false); event("off-while-asleep"); hold(2500)
            assertTrue(shell("dumpsys power").contains("mWakefulness=Asleep"))
            stopClip()
            // Bring a non-network target to the front while asleep, then wake.
            shell("am start -W -n ${context.packageName}/.MainActivity")
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard"); hold(1800)
            dashboard(); click(R.string.diagnostics_and_logs); scroll(text(R.string.recent_logs))
            startClip("screen-log"); hold(3800); event("verified-screen-off-result"); stopClip()
            dashboard()
            settings.updateSettings { s -> s.copy(appExitDisconnectEnabled=true, screenOffDisconnectEnabled=false) }

            openAppDetails("联网演示")
            scroll(text(R.string.activity_enable_rules)); hold(1200)
            startClip("activity-enabled"); hold(2500); stopClip()
            scroll(second).performTouchInput { click() }; hold(1000)
            scroll(text(R.string.activity_follow_app)); hold(1400)
            startClip("activity-edit")
            hold(1500); click(R.string.offline); hold(2300)
            click(R.string.activity_save); event("activity-offline-saved"); hold(2000)
            scroll(second).performTouchInput { click() }; hold(900)
            scroll(text(R.string.offline)); hold(2600); stopClip(); back()
            assertEquals(ActivityRuleMode.Offline.name, repo.observeDetails(fixture).first().rules.first { r -> r.activityName==second }.mode)

            shell("input keyevent KEYCODE_HOME"); hold(800)
            startClip("activity-demo")
            shell("am start -W -n $fixture/.FirstActivity"); awaitData(true); hold(3000)
            event("enter-offline"); assertTrue(nativeClick("进入离线功能")); awaitData(false); hold(2300)
            stopClip()

            val component="${context.packageName}/.quickrule.QuickRuleTileService"
            shell("cmd statusbar add-tile $component")
            shell("settings put secure sysui_qs_tiles custom($component)")
            startClip("quick-flow")
            hold(2000); event("expand-quick-settings"); shell("cmd statusbar expand-settings"); hold(2400)
            shell("cmd statusbar click-tile $component")
            compose.waitUntil(15000) { exists(text(R.string.quick_rule_scope_app)) }
            hold(1800)
            compose.onNodeWithText(text(R.string.quick_rule_scope_activity)).performScrollTo().performTouchInput { click() }; hold(2400)
            compose.onNodeWithText(text(R.string.quick_rule_auto)).performScrollTo().performTouchInput { click() }; event("select-auto"); hold(2700)
            compose.onNodeWithText(text(R.string.activity_save)).performTouchInput { click() }; event("quick-saved"); hold(2800)
            stopClip()
            assertEquals(ActivityRuleMode.FollowApp.name, repo.observeDetails(fixture).first().rules.first { r -> r.activityName==second }.mode)
            shell("cmd statusbar collapse")
            openAppDetails("联网演示"); scroll(second).performTouchInput { click() }; hold(1000)
            scroll(text(R.string.activity_follow_app)); startClip("quick-result"); hold(3600); stopClip()

            dashboard(); click(R.string.automation_rules); click(R.string.app_exit_delay_in_seconds)
            startClip("delay-change"); hold(1200)
            compose.onAllNodesWithText("+").onFirst().performScrollTo().performTouchInput { click() }; event("delay-ten"); hold(3400)
            stopClip()
            settings.updateSettings { s -> s.copy(appExitDelaySeconds=5) }
            dashboard(); startClip("pause-flow"); hold(1300)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onAllNodes(isToggleable()).onFirst().performTouchInput { click() }; hold(2700)
            assertTrue(exists(text(R.string.automation_paused))); stopClip()
            settings.updateSettings { s -> s.withAutomationEnabled() }
            dashboard(); click(R.string.diagnostics_and_logs); scroll(text(R.string.recent_logs))
            startClip("logs-flow"); hold(3800); stopClip()
            dashboard(); click(R.string.appearance)
            startClip("appearance-flow"); hold(1800); click(R.string.dark); hold(2600); stopClip()
            settings.updateSettings { s -> s.copy(themeMode=ThemeMode.Light, themePalette=ThemePalette.WarmPaper) }
            dashboard(); startClip("dashboard-running"); hold(3000); stopClip()
            sampling=false; sampler.join(3000)
          } catch (failure: Throwable) { sampling=false; capture("failure"); throw failure }
        }
    }
}
