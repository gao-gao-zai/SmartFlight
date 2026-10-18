package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.permission.AccessResultFormatter
import com.gaozay.smartflight.permission.FakeSettingsRepository
import com.gaozay.smartflight.permission.FakeRuntimeStatusRepository
import com.gaozay.smartflight.permission.FakeExecutionLogRepository
import com.gaozay.smartflight.permission.AccessExecutionLogWriter
import com.gaozay.smartflight.permission.AccessRuntimeSnapshotUpdater
import com.gaozay.smartflight.permission.ExecutorNetworkControlProbe
import com.gaozay.smartflight.permission.NetworkControlActionExecutor
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.domain.model.UnifiedNetworkState
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExecutorProbeServiceTest : LocalizedStringsTest() {
    private val formatter = AccessResultFormatter()

    @Test fun successfulNoOpWriteReportsObservedStateAndFailure() = runTest {
        val f = Fixture()
        f.runner.mutate = false
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true, false)
        assertEquals(false, result.controlledEnabled)
        assertEquals(ExecutionResult.Failed, formatter.executionResultFor(result))
        assertEquals(6, f.runner.calls.size)
    }

    @Test fun delayedRadioSettingIsConfirmedWithinVerificationWindow() = runTest {
        val f = Fixture()
        f.runner.readsBeforeApply = 3
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true)
        assertEquals(true, result.controlledEnabled)
        assertEquals(ExecutionResult.Success, formatter.executionResultFor(result))
        assertEquals(5, f.runner.calls.size)
    }

    @Test fun staleKnownStateCannotSkipReconnect() = runTest {
        val f = Fixture()
        f.runner.airplane = true
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, false, false)
        assertFalse(result.alreadyInRequestedState)
        assertFalse(f.runner.airplane)
        assertEquals(ExecutionResult.Success, formatter.executionResultFor(result))
    }

    @Test fun nonzeroExitWithVerifiedTargetRetainsExitAndReportsSuccess() = runTest {
        val f = Fixture()
        f.runner.writeExit = 1
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true)
        assertEquals(1, result.exitCode)
        assertEquals(true, result.controlledEnabled)
        assertEquals(ExecutionResult.Success, formatter.executionResultFor(result))
    }

    @Test fun nonzeroExitWithUnchangedReadbackPreservesFalse() = runTest {
        val f = Fixture()
        f.runner.writeExit = 1
        f.runner.mutate = false
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true)
        assertEquals(false, result.controlledEnabled)
        assertEquals(ExecutionResult.Failed, formatter.executionResultFor(result))
    }

    @Test fun unreadableReadbackNeverInventsTarget() = runTest {
        val f = Fixture()
        f.runner.unknownAfterWrite = true
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true)
        assertNull(result.controlledEnabled)
        assertEquals(ExecutionResult.PartialSuccess, formatter.executionResultFor(result))
    }

    @Test fun failedReadWithBinaryOutputIsNotAnObservation() = runTest {
        val f = Fixture()
        f.runner.readExit = 1
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true, false)
        assertNull(result.controlledEnabled)
        assertEquals(1, f.runner.calls.size)
    }

    @Test fun concurrentTogglesReadFreshStateInsideTransaction() = runTest {
        val f = Fixture()
        f.runner.waitMillis = 10
        listOf(async { f.service.toggleCurrentNetworkControlState(false) },
            async { f.service.toggleCurrentNetworkControlState(false) }).awaitAll()
        assertFalse(f.runner.airplane)
        assertEquals(listOf(true, false), f.runner.airplaneWrites)
    }

    @Test fun staleMobileDisconnectedHintCannotSkipEnable() = runTest {
        val f = Fixture(NetworkControlMode.MobileData)
        f.runner.mobile = false
        val result = f.service.setDisconnectedState(false, false)
        assertTrue(f.runner.mobile)
        assertEquals(true, result.controlledEnabled)
    }

    @Test fun modeIsCapturedBeforeSuspendedRead() = runTest {
        val f = Fixture()
        f.runner.onRead = { f.settings.updateSettings { it.copy(networkControlMode = NetworkControlMode.MobileData) } }
        f.service.setDisconnectedState(true)
        assertEquals(listOf(true), f.runner.airplaneWrites)
        assertTrue(f.runner.mobile)
    }

    @Test fun suspendedRunnerTimesOutAndReleasesChangeLock() = runTest {
        val f = Fixture()
        f.runner.waitMillis = 100_000
        val result = f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true)
        assertEquals(ExecutionResult.Failed, formatter.executionResultFor(result))
        f.runner.waitMillis = 0
        assertEquals(true, f.service.setNetworkControlEnabled(NetworkControlMode.AirplaneMode, true).controlledEnabled)
    }

    @Test fun realActionAndProbeKeepObservedFalseWhenRestoreFails() = runTest {
        val f = Fixture()
        f.runner.airplane = true
        f.runner.mobile = false
        f.runner.failMobileWrites = true
        val status = FakeRuntimeStatusRepository(RuntimeSnapshot(isAirplaneModeEnabled = true,
            isMobileDataEnabled = false, rememberedMobileDataEnabledBeforeAirplaneMode = true))
        f.action(status).setDisconnectedState(false, TriggerSource.Manual, "test")
        assertEquals(false, status.currentSnapshot.isAirplaneModeEnabled)
        assertEquals(false, status.currentSnapshot.isMobileDataEnabled)
        assertEquals(ExecutionResult.PartialSuccess, status.currentSnapshot.lastActionResult)
        assertEquals(true, status.currentSnapshot.rememberedMobileDataEnabledBeforeAirplaneMode)
        assertEquals(UnifiedNetworkState.Offline, status.currentSnapshot.unifiedNetworkState)
    }

    @Test fun realActionAndProbePreserveUnknownAndRetryMemory() = runTest {
        val f = Fixture()
        f.runner.airplane = true
        f.runner.mobile = false
        f.runner.unknownMobileAfterWrite = true
        val status = FakeRuntimeStatusRepository(RuntimeSnapshot(isAirplaneModeEnabled = true,
            isMobileDataEnabled = false, rememberedMobileDataEnabledBeforeAirplaneMode = true))
        f.action(status).setDisconnectedState(false, TriggerSource.Manual, "test")
        assertNull(status.currentSnapshot.isMobileDataEnabled)
        assertEquals(ExecutionResult.PartialSuccess, status.currentSnapshot.lastActionResult)
        assertEquals(true, status.currentSnapshot.rememberedMobileDataEnabledBeforeAirplaneMode)
        assertEquals(UnifiedNetworkState.Unknown, status.currentSnapshot.unifiedNetworkState)
    }

    @Test fun completeConcurrentManualActionsAreSerializedIncludingRestoreAndSnapshot() = runTest {
        val f = Fixture()
        f.runner.waitMillis = 10
        val status = FakeRuntimeStatusRepository(RuntimeSnapshot(isAirplaneModeEnabled = false, isMobileDataEnabled = true))
        val logs = FakeExecutionLogRepository()
        val action = f.action(status, logs)
        listOf(async { action.toggleCurrentNetworkControlState() },
            async { action.toggleCurrentNetworkControlState() }).awaitAll()
        assertFalse(f.runner.airplane)
        assertEquals(false, status.currentSnapshot.isAirplaneModeEnabled)
        assertNull(status.currentSnapshot.rememberedMobileDataEnabledBeforeAirplaneMode)
        assertEquals(2, logs.logs.size)
    }

    private class Fixture(mode: NetworkControlMode = NetworkControlMode.AirplaneMode) {
        val runner = Runner()
        val settings = FakeSettingsRepository(UserSettings(networkControlMode = mode))
        val service = ExecutorProbeService(settings, { SelectedExecutorRunner(ExecutorType.Root, runner) },
            NetworkControlCommandMapper(), MobileDataSupportChecker())
        fun action(status: FakeRuntimeStatusRepository, logs: FakeExecutionLogRepository = FakeExecutionLogRepository()): NetworkControlActionExecutor {
            val formatter = AccessResultFormatter()
            return NetworkControlActionExecutor(settings, status, ExecutorNetworkControlProbe(service), formatter,
                AccessRuntimeSnapshotUpdater(status, formatter), AccessExecutionLogWriter(logs, status))
        }
    }

    private class Runner : ExecutorCommandRunner {
        var airplane = false
        var mobile = true
        var mutate = true
        var writeExit = 0
        var readExit = 0
        var unknownAfterWrite = false
        var failMobileWrites = false
        var unknownMobileAfterWrite = false
        private var mobileWrote = false
        var waitMillis = 0L
        var onRead: (suspend () -> Unit)? = null
        var readsBeforeApply = 0
        private var pendingAirplane: Boolean? = null
        val calls = mutableListOf<String>()
        val airplaneWrites = mutableListOf<Boolean>()
        private var wrote = false
        override suspend fun run(command: ExecutorCommand): ExecutorCommandResult {
            val c = command.rawCommand
            calls += c
            delay(waitMillis)
            var exit = 0
            val output = when {
                c == "service check phone" -> "Service phone: found"
                c.startsWith("settings get") -> {
                    onRead?.invoke()
                    pendingAirplane?.let {
                        if (--readsBeforeApply <= 0) { airplane = it; pendingAirplane = null }
                    }
                    exit = readExit
                    if ((wrote && unknownAfterWrite) || (mobileWrote && unknownMobileAfterWrite && c.endsWith("mobile_data"))) "unknown" else {
                        val state = if (c.endsWith("airplane_mode_on")) airplane else mobile
                        if (state) "1" else "0"
                    }
                }
                else -> {
                    wrote = true
                    exit = writeExit
                    val enabled = c.contains("enable")
                    if (c.startsWith("svc data")) {
                        mobileWrote = true
                        if (failMobileWrites) exit = 1 else if (mutate) mobile = enabled
                    } else {
                        airplaneWrites += enabled
                        if (mutate) {
                            if (readsBeforeApply > 0) pendingAirplane = enabled else airplane = enabled
                        }
                    }
                    ""
                }
            }
            return ExecutorCommandResult(ExecutorType.Root, executed = true, exitCode = exit, stdout = output, summary = "fake")
        }
    }
}
