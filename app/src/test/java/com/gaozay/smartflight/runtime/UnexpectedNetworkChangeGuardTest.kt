package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnexpectedNetworkChangeGuardTest {
    @Test
    fun externalNetworkControlChangePausesAutomationUntilAppSwitch() = runTest {
        val settingsRepository = FakeSettingsRepository(
            UserSettings(automationEnabled = true),
        )
        val runtimeStatusRepository = FakeRuntimeStatusRepository()
        val promptNotifier = NoOpRuntimePromptNotifier()
        val guard = UnexpectedNetworkChangeGuard(
            settingsRepository = settingsRepository,
            reporter = RuntimeSnapshotReporter(runtimeStatusRepository),
            promptNotifier = promptNotifier,
            expectedNetworkChangeTracker = RuntimeExpectedNetworkChangeTracker(),
        )

        val updatedState = guard.handleNetworkStateObserved(
            state = RuntimeState(
                settings = settingsRepository.currentSettings,
                lastKnownForegroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            ),
            previousSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
            updatedSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = true),
        )

        assertEquals(AutomationDisableMode.UntilAppSwitch, settingsRepository.currentSettings.temporaryDisableMode)
        assertEquals("com.example.app", settingsRepository.currentSettings.temporaryDisableForegroundPackageName)
        assertEquals(AutomationDisableMode.UntilAppSwitch, updatedState.settings.temporaryDisableMode)
        assertEquals(
            listOf("检测到联网状态被外部改变，已暂停自动化直到应用切换"),
            promptNotifier.automationPausedPrompts,
        )
    }

    @Test
    fun expectedNetworkControlChangeDoesNotPauseAutomation() = runTest {
        val settingsRepository = FakeSettingsRepository(
            UserSettings(automationEnabled = true),
        )
        val tracker = RuntimeExpectedNetworkChangeTracker().apply {
            record(targetDisconnected = true)
        }
        val promptNotifier = NoOpRuntimePromptNotifier()
        val guard = UnexpectedNetworkChangeGuard(
            settingsRepository = settingsRepository,
            reporter = RuntimeSnapshotReporter(FakeRuntimeStatusRepository()),
            promptNotifier = promptNotifier,
            expectedNetworkChangeTracker = tracker,
        )

        guard.handleNetworkStateObserved(
            state = RuntimeState(settings = settingsRepository.currentSettings),
            previousSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
            updatedSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = true),
        )

        assertEquals(AutomationDisableMode.None, settingsRepository.currentSettings.temporaryDisableMode)
        assertEquals(emptyList<String>(), promptNotifier.automationPausedPrompts)
    }

    @Test
    fun disabledSettingDoesNotPauseAutomationOnExternalNetworkControlChange() = runTest {
        val settingsRepository = FakeSettingsRepository(
            UserSettings(
                automationEnabled = true,
                pauseAutomationOnExternalNetworkChange = false,
            ),
        )
        val promptNotifier = NoOpRuntimePromptNotifier()
        val guard = UnexpectedNetworkChangeGuard(
            settingsRepository = settingsRepository,
            reporter = RuntimeSnapshotReporter(FakeRuntimeStatusRepository()),
            promptNotifier = promptNotifier,
            expectedNetworkChangeTracker = RuntimeExpectedNetworkChangeTracker(),
        )

        guard.handleNetworkStateObserved(
            state = RuntimeState(settings = settingsRepository.currentSettings),
            previousSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
            updatedSnapshot = RuntimeSnapshot(isAirplaneModeEnabled = true),
        )

        assertEquals(AutomationDisableMode.None, settingsRepository.currentSettings.temporaryDisableMode)
        assertEquals(emptyList<String>(), promptNotifier.automationPausedPrompts)
    }
}
