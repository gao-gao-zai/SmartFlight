package com.gaozay.smartflight.i18n

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class LocalizationStartupTest {
    @Test
    fun setupScreenAndRuntimeResourcesFollowApplicationLocaleChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = instrumentation.targetContext.getSystemService(LocaleManager::class.java)
        val originalLocales = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("en")
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitSetup("SmartFlight setup check")
                assertEquals("SmartFlight", AppStrings.get(R.string.app_name))
                capture("setup-en.png")

                manager.applicationLocales = LocaleList.forLanguageTags("zh")
                awaitSetup("SmartFlight \u63a5\u5165\u68c0\u67e5")
                assertEquals("\u81ea\u52a8\u98de\u884c", AppStrings.get(R.string.app_name))
                capture("setup-zh.png")

                manager.applicationLocales = LocaleList.forLanguageTags("en")
                awaitSetup("SmartFlight setup check")
                assertEquals("SmartFlight", AppStrings.get(R.string.app_name))
                capture("setup-en-after-switch.png")
            }
        } finally {
            manager.applicationLocales = originalLocales
        }
    }

    private fun awaitSetup(heading: String) {
        awaitText(heading)
        // This smoke test runs on a fresh, non-rooted Google APIs emulator.
        // The recreated heading appears before onResume's asynchronous access refresh.
        awaitText(AppStrings.get(R.string.root_binary_not_detected))
    }

    private fun awaitText(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 15_000
        var texts = emptyList<String>()
        while (SystemClock.uptimeMillis() < deadline) {
            // Compose exposes virtual nodes; walk them instead of relying on the
            // platform provider's optional findAccessibilityNodeInfosByText implementation.
            texts = visibleTexts(automation.rootInActiveWindow)
            if (texts.any { it.contains(text) }) return
            SystemClock.sleep(100)
        }
        capture("startup-failure.png")
        throw AssertionError("Localized setup text was not displayed: $text. Visible text: $texts")
    }

    private fun visibleTexts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        return buildList {
            node.text?.toString()?.let(::add)
            node.contentDescription?.toString()?.let(::add)
            for (index in 0 until node.childCount) addAll(visibleTexts(node.getChild(index)))
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        // Keep captures outside the package directory, which Gradle may remove on uninstall.
        shell("mkdir -p /sdcard/Download/smartflight-localization")
        shell("cp ${file.absolutePath} /sdcard/Download/smartflight-localization/$name")
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val message = ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
        assertTrue(message, message.isBlank())
    }
}
