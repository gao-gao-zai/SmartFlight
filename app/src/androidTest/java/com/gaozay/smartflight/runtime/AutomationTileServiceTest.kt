package com.gaozay.smartflight.runtime

import android.graphics.Bitmap
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.R
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.DataStoreSettingsRepository
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomationTileServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val component = "${context.packageName}/.runtime.AutomationTileService"

    @Test
    fun rapidClicksAndBackgroundRestoreKeepTheVisibleSystemTileInSync() = runBlocking {
        val repository = DataStoreSettingsRepository(context)
        val original = repository.settings.first()
        val originalTiles = shell("settings get secure sysui_qs_tiles").trim()
        try {
            repository.updateSettings { it.withAutomationDisabled(AutomationDisableMode.For1Minute) }
            shell("cmd statusbar add-tile $component")
            // Keep the tested tile on the first page regardless of the emulator's defaults.
            shell("settings put secure sysui_qs_tiles 'custom($component)'")
            shell("cmd statusbar expand-settings")
            waitForSubtitle(AutomationDisableMode.For1Minute)

            repeat(4) { shell("cmd statusbar click-tile $component") }
            waitForSubtitle(AutomationDisableMode.For30Minutes)
            assertEquals(AutomationDisableMode.For30Minutes, repository.settings.first().temporaryDisableMode)
            screenshot("rapid-clicks")

            // The panel stays open while the runtime/app restores automation.
            repository.updateSettings { it.withAutomationEnabled() }
            waitForSubtitle(AutomationDisableMode.None)
            screenshot("background-restore")

            shell("cmd statusbar collapse")
            repository.updateSettings { it.withAutomationDisabled(AutomationDisableMode.For10Minutes) }
            shell("cmd statusbar expand-settings")
            waitForSubtitle(AutomationDisableMode.For10Minutes)
            screenshot("reopened-panel")
        } finally {
            shell("cmd statusbar collapse")
            shell("cmd statusbar remove-tile $component")
            if (originalTiles == "null") {
                shell("settings delete secure sysui_qs_tiles")
            } else {
                shell("settings put secure sysui_qs_tiles '$originalTiles'")
            }
            repository.updateSettings { original }
            context.stopService(Intent(context, AutomationForegroundService::class.java))
        }
    }

    private fun waitForSubtitle(mode: AutomationDisableMode) {
        val label = context.getString(R.string.automation_tile_label)
        val subtitle = context.getString(mode.tileLabelRes)
        val deadline = SystemClock.uptimeMillis() + 15_000L
        var visible = ""
        while (SystemClock.uptimeMillis() < deadline) {
            visible = automation.rootInActiveWindow?.let { root ->
                try {
                    if (root.packageName == "com.android.systemui") visibleText(root) else ""
                } finally {
                    root.recycle()
                }
            }.orEmpty()
            if (visible.contains(label) && visible.contains(subtitle)) return
            SystemClock.sleep(100)
        }
        screenshot("tile-failure")
        assertTrue("Expected tile '$label / $subtitle', visible System UI: $visible", false)
    }

    private fun visibleText(node: AccessibilityNodeInfo): String = buildString {
        append(node.text.orEmpty()).append(' ').append(node.contentDescription.orEmpty()).append('\n')
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                try {
                    append(visibleText(child))
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {
            it.readText()
        }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val file = File(checkNotNull(context.getExternalFilesDir(null)), "$name.png")
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        shell("mkdir -p /sdcard/Download/smartflight-tiles")
        shell("cp ${file.absolutePath} /sdcard/Download/smartflight-tiles/$name.png")
    }
}
