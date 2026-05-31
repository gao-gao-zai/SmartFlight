package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkControlActionExecutorTest {
    @Test
    fun mobileDataDisconnectUsesSetDisconnectedStateAndWritesLog() = runTest {
        val fixture = fixture(
            settings = UserSettings(networkControlMode = NetworkControlMode.MobileData),
            snapshot = RuntimeSnapshot(isMobileDataEnabled = true),
        )
        fixture.probe.setDisconnectedResult = executorResult(
            controlMode = NetworkControlMode.MobileData,
            controlledEnabled = false,
            summary = "移动数据已关闭",
        )

        fixture.executor.setDisconnectedState(
            disconnected = true,
            triggerSource = TriggerSource.Manual,
            reason = "手动关闭移动数据",
        )

        assertEquals(listOf(true), fixture.probe.setDisconnectedRequests)
        assertEquals(false, fixture.runtimeStatusRepository.currentSnapshot.isMobileDataEnabled)
        assertEquals(1, fixture.executionLogRepository.logs.size)
    }

    @Test
    fun airplaneDisconnectRemembersMobileDataState() = runTest {
        val fixture = fixture(
            settings = UserSettings(networkControlMode = NetworkControlMode.AirplaneMode),
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = false, isMobileDataEnabled = true),
        )
        fixture.probe.setNetworkControlResults.add(
            executorResult(
                controlMode = NetworkControlMode.AirplaneMode,
                controlledEnabled = true,
                summary = "飞行模式已开启",
            ),
        )

        fixture.executor.setDisconnectedState(
            disconnected = true,
            triggerSource = TriggerSource.Manual,
            reason = "手动开启飞行模式",
        )

        assertEquals(true, fixture.runtimeStatusRepository.currentSnapshot.rememberedMobileDataEnabledBeforeAirplaneMode)
        assertEquals(true, fixture.runtimeStatusRepository.currentSnapshot.isAirplaneModeEnabled)
    }

    @Test
    fun airplaneReconnectRestoreFailureBecomesPartialSuccess() = runTest {
        val fixture = fixture(
            settings = UserSettings(networkControlMode = NetworkControlMode.AirplaneMode),
            snapshot = RuntimeSnapshot(
                isAirplaneModeEnabled = true,
                isMobileDataEnabled = false,
                rememberedMobileDataEnabledBeforeAirplaneMode = true,
            ),
        )
        fixture.probe.setNetworkControlResults.add(
            executorResult(
                controlMode = NetworkControlMode.AirplaneMode,
                controlledEnabled = false,
                summary = "飞行模式已关闭",
            ),
        )
        fixture.probe.setNetworkControlResults.add(
            executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = null,
                executed = false,
                exitCode = 1,
                summary = "移动数据写入失败",
            ),
        )

        fixture.executor.setDisconnectedState(
            disconnected = false,
            triggerSource = TriggerSource.Manual,
            reason = "手动关闭飞行模式",
        )

        assertEquals(ExecutionResult.PartialSuccess, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
        assertEquals(2, fixture.probe.setNetworkControlEnabledRequests.size)
    }

    @Test
    fun manualAirplaneToggleUnknownStateWritesFailedProbeResult() = runTest {
        val fixture = fixture(
            settings = UserSettings(networkControlMode = NetworkControlMode.AirplaneMode),
            snapshot = RuntimeSnapshot(isAirplaneModeEnabled = null),
        )
        fixture.probe.networkProbeResults[NetworkControlMode.AirplaneMode] = ArrayDeque(
            listOf(
                executorResult(
                    controlMode = NetworkControlMode.AirplaneMode,
                    controlledEnabled = null,
                    executed = false,
                    exitCode = null,
                    summary = "无法解析当前飞行模式状态",
                ),
                executorResult(
                    controlMode = NetworkControlMode.AirplaneMode,
                    controlledEnabled = null,
                    executed = false,
                    exitCode = null,
                    summary = "无法解析当前飞行模式状态",
                ),
            ),
        )

        fixture.executor.toggleCurrentNetworkControlState()

        assertEquals(ExecutionResult.Failed, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
        assertEquals(1, fixture.executionLogRepository.logs.size)
    }

    private fun fixture(
        settings: UserSettings,
        snapshot: RuntimeSnapshot,
    ): Fixture {
        val runtimeStatusRepository = FakeRuntimeStatusRepository(snapshot)
        val executionLogRepository = FakeExecutionLogRepository()
        val probe = FakeNetworkControlProbe()
        val formatter = AccessResultFormatter()
        val snapshotUpdater = AccessRuntimeSnapshotUpdater(runtimeStatusRepository, formatter)
        val logWriter = AccessExecutionLogWriter(executionLogRepository, runtimeStatusRepository)
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            executionLogRepository = executionLogRepository,
            probe = probe,
            executor = NetworkControlActionExecutor(
                settingsRepository = FakeSettingsRepository(settings),
                runtimeStatusRepository = runtimeStatusRepository,
                networkControlProbe = probe,
                formatter = formatter,
                snapshotUpdater = snapshotUpdater,
                logWriter = logWriter,
            ),
        )
    }

    private data class Fixture(
        val runtimeStatusRepository: FakeRuntimeStatusRepository,
        val executionLogRepository: FakeExecutionLogRepository,
        val probe: FakeNetworkControlProbe,
        val executor: NetworkControlActionExecutor,
    )
}
