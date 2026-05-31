package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAutomationHandlerTest {
    @Test
    fun firstSyncUpdatesTargetStateWithoutNetworkRequest() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = false),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = null,
        )

        val updated = fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(true, updated.lastTargetAppActive)
        assertTrue(fixture.accessRepository.disconnectedRequests.isEmpty())
    }

    @Test
    fun targetAppEnteringForegroundReconnects() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = true,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
    }

    @Test
    fun wifiSkipReconnectLogsSkippedDecision() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.app", "Example", 1_000L),
            snapshot = RuntimeSnapshot(isWifiConnected = true, isAirplaneModeEnabled = true),
        )
        val state = RuntimeState(
            settings = UserSettings(automationEnabled = true, skipReconnectOnWifi = true),
            appRulesByPackageName = mapOf(
                "com.example.app" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(ExecutionAction.CancelScheduledDisconnect, fixture.runtimeStatusRepository.currentSnapshot.lastAction)
        assertEquals(ExecutionResult.Skipped, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
    }

    @Test
    fun temporaryDisableUntilAppSwitchClearsAndContinuesEvaluating() = runTest {
        val fixture = fixture(
            foregroundApp = ForegroundAppInfo("com.example.next", "Next", 1_000L),
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = true,
                lastActionResult = ExecutionResult.Success,
            ),
        )
        val state = RuntimeState(
            settings = UserSettings(
                automationEnabled = true,
                temporaryDisableMode = AutomationDisableMode.UntilAppSwitch,
                temporaryDisableForegroundPackageName = "com.example.old",
            ),
            appRulesByPackageName = mapOf(
                "com.example.next" to AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual),
            ),
            lastTargetAppActive = false,
        )

        val updated = fixture.handler.automationTick(state, fixture.scheduler)

        assertEquals(AutomationDisableMode.None, updated.settings.temporaryDisableMode)
        assertEquals(listOf(false), fixture.accessRepository.disconnectedRequests)
    }

    private fun fixture(
        foregroundApp: ForegroundAppInfo,
        snapshot: RuntimeSnapshot,
    ): Fixture {
        val runtimeStatusRepository = FakeRuntimeStatusRepository(snapshot)
        val reporter = RuntimeSnapshotReporter(runtimeStatusRepository)
        val accessRepository = FakeAccessRepository(availableAccessGateState())
        val scheduler = RuntimeTaskScheduler(
            scope = TestScope(StandardTestDispatcher()),
            send = {},
            reporter = reporter,
        )
        val networkExecutor = RuntimeNetworkChangeExecutor(
            accessRepository = accessRepository,
            reporter = reporter,
            promptNotifier = NoOpRuntimePromptNotifier(),
        )
        val disconnectHandler = DisconnectAutomationHandler(
            accessRepository = accessRepository,
            automationRuleEngine = AutomationRuleEngine(ForegroundRuleEvaluator()),
            reporter = reporter,
            networkChangeExecutor = networkExecutor,
        )
        val settingsRepository = FakeSettingsRepository()
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            accessRepository = accessRepository,
            scheduler = scheduler,
            handler = ForegroundAutomationHandler(
                accessRepository = accessRepository,
                foregroundAppSource = FakeForegroundAppSource(foregroundApp),
                automationRuleEngine = AutomationRuleEngine(ForegroundRuleEvaluator()),
                reporter = reporter,
                networkChangeExecutor = networkExecutor,
                disconnectAutomationHandler = disconnectHandler,
                temporaryDisableHandler = TemporaryDisableHandler(settingsRepository, reporter),
            ),
        )
    }

    private data class Fixture(
        val runtimeStatusRepository: FakeRuntimeStatusRepository,
        val accessRepository: FakeAccessRepository,
        val scheduler: RuntimeTaskScheduler,
        val handler: ForegroundAutomationHandler,
    )
}
