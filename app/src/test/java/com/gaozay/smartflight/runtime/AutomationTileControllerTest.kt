package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationTileControllerTest {
    @Test
    fun rapidClicksDuringSlowWritesAdvanceEveryModeAndRenderPersistedState() = runTest {
        val settings = SlowSettingsRepository(UserSettings(automationEnabled = true))
        val rendered = mutableListOf<AutomationDisableMode>()
        val startedAtModes = mutableListOf<AutomationDisableMode>()
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, rendered::add,
            { startedAtModes += settings.settings.value.temporaryDisableMode },
        )
        controller.startListening()
        runCurrent()

        repeat(20) { launch { controller.onClick() } }
        advanceUntilIdle()
        runCurrent()

        val cycle = listOf(
            AutomationDisableMode.UntilAppSwitch, AutomationDisableMode.UntilScreenOff,
            AutomationDisableMode.For1Minute, AutomationDisableMode.For5Minutes,
            AutomationDisableMode.For10Minutes, AutomationDisableMode.For20Minutes,
            AutomationDisableMode.For30Minutes, AutomationDisableMode.Permanent,
            AutomationDisableMode.None,
        )
        assertEquals(List(20) { cycle[it % cycle.size] }, settings.writtenModes)
        assertEquals(20, startedAtModes.size)
        assertTrue(settings.settings.value.automationEnabled)
        assertEquals(AutomationDisableMode.UntilScreenOff, settings.settings.value.temporaryDisableMode)
        assertEquals(AutomationDisableMode.UntilScreenOff, rendered.last())
    }

    @Test
    fun backgroundRestoreAndAppSettingsChangesRefreshAnAlreadyVisibleTile() = runTest {
        val settings = FakeSettingsRepository(
            UserSettings(automationEnabled = true).withAutomationDisabled(AutomationDisableMode.For1Minute),
        )
        val rendered = mutableListOf<AutomationDisableMode>()
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, rendered::add, {},
        )
        controller.startListening()
        runCurrent()
        assertEquals(listOf(AutomationDisableMode.For1Minute), rendered)

        settings.updateSettings { it.withAutomationEnabled() }
        runCurrent()
        settings.updateSettings { it.withAutomationDisabled(AutomationDisableMode.Permanent) }
        runCurrent()
        settings.updateSettings { it.copy(screenOffDelaySeconds = 90) }
        runCurrent()

        assertEquals(
            listOf(AutomationDisableMode.For1Minute, AutomationDisableMode.None, AutomationDisableMode.Permanent),
            rendered,
        )
    }

    @Test
    fun stoppingListeningDoesNotCancelClickAndReopeningRendersTheLatestState() = runTest {
        val settings = SlowSettingsRepository(UserSettings(automationEnabled = true))
        val rendered = mutableListOf<AutomationDisableMode>()
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, rendered::add, {},
        )
        controller.startListening()
        runCurrent()
        val click = launch { controller.onClick() }
        runCurrent()
        controller.stopListening()
        advanceUntilIdle()
        runCurrent()
        assertTrue(click.isCompleted)
        assertEquals(AutomationDisableMode.UntilAppSwitch, settings.settings.value.temporaryDisableMode)
        assertEquals(listOf(AutomationDisableMode.None), rendered)

        controller.refresh()
        runCurrent()
        assertEquals(1, rendered.size)
        controller.startListening()
        runCurrent()
        assertEquals(AutomationDisableMode.UntilAppSwitch, rendered.last())
    }

    @Test
    fun configurationRefreshRendersCurrentModeAndCancelsThePreviousListener() = runTest {
        val settings = FakeSettingsRepository(UserSettings())
        val rendered = mutableListOf<AutomationDisableMode>()
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, rendered::add, {},
        )
        controller.startListening()
        runCurrent()
        controller.refresh()
        runCurrent()
        assertEquals(listOf(AutomationDisableMode.Permanent, AutomationDisableMode.Permanent), rendered)

        settings.updateSettings { it.withAutomationEnabled() }
        runCurrent()
        assertEquals(3, rendered.size)
        assertEquals(AutomationDisableMode.None, rendered.last())
    }

    @Test
    fun modeIsComputedInsideUpdateUsingLatestSettingsAndForegroundPackage() = runTest {
        val backing = FakeSettingsRepository(UserSettings(automationEnabled = true))
        val settings = object : SettingsRepository by backing {
            override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
                // Simulate another writer committing before the tile's transaction.
                backing.updateSettings { it.withAutomationDisabled(AutomationDisableMode.For5Minutes) }
                backing.updateSettings(transform)
            }
        }
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, {}, {},
        )
        controller.onClick()
        assertEquals(AutomationDisableMode.For10Minutes, backing.currentSettings.temporaryDisableMode)

        val runtime = FakeRuntimeStatusRepository(RuntimeSnapshot(currentForegroundPackageName = "com.example.target"))
        val foregroundSettings = FakeSettingsRepository(UserSettings(automationEnabled = true))
        val foregroundController = AutomationTileController(
            foregroundSettings, runtime, backgroundScope, {}, {},
        )
        foregroundController.onClick()
        assertEquals("com.example.target", foregroundSettings.currentSettings.temporaryDisableForegroundPackageName)
    }

    @Test
    fun visibleStateStillUpdatesIfServiceStartupFailsAfterSaving() = runTest {
        val settings = FakeSettingsRepository(UserSettings())
        val rendered = mutableListOf<AutomationDisableMode>()
        val controller = AutomationTileController(
            settings, FakeRuntimeStatusRepository(), backgroundScope, rendered::add,
            { throw IllegalStateException("Service start failed") },
        )
        controller.startListening()
        runCurrent()
        val result = runCatching { controller.onClick() }
        runCurrent()
        assertTrue(result.isFailure)
        assertTrue(settings.currentSettings.automationEnabled)
        assertEquals(AutomationDisableMode.None, rendered.last())
        assertFalse(settings.currentSettings.isTemporaryDisableActive())
    }

    private class SlowSettingsRepository(initial: UserSettings) : SettingsRepository {
        override val settings = MutableStateFlow(initial)
        private val mutex = Mutex()
        val writtenModes = mutableListOf<AutomationDisableMode>()

        override suspend fun setAutomationEnabled(enabled: Boolean) {
            updateSettings { it.copy(automationEnabled = enabled) }
        }

        override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
            mutex.withLock {
                delay(100)
                settings.value = transform(settings.value)
                writtenModes += if (settings.value.automationEnabled) {
                    settings.value.temporaryDisableMode
                } else {
                    AutomationDisableMode.Permanent
                }
            }
        }
    }
}
