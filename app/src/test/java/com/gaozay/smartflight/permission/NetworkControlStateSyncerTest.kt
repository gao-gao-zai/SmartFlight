package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkControlStateSyncerTest {
    @Test
    fun serviceRestoredSyncUpdatesSnapshotWithoutLog() = runTest {
        val fixture = fixture(
            probeResult = executorResult(
                controlMode = NetworkControlMode.AirplaneMode,
                controlledEnabled = true,
            ),
        )

        fixture.syncer.syncCurrentNetworkControlState()

        assertEquals(true, fixture.runtimeStatusRepository.currentSnapshot.isAirplaneModeEnabled)
        assertEquals(TriggerSource.ServiceRestored, fixture.runtimeStatusRepository.currentSnapshot.lastTriggerSource)
        assertTrue(fixture.executionLogRepository.logs.isEmpty())
    }

    @Test
    fun manualProbeWritesLogAndUpdatesResult() = runTest {
        val fixture = fixture(
            probeResult = executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = false,
            ),
        )

        fixture.syncer.probeCurrentNetworkControlState()

        assertEquals(false, fixture.runtimeStatusRepository.currentSnapshot.isMobileDataEnabled)
        assertEquals(ExecutionAction.DoNothing, fixture.runtimeStatusRepository.currentSnapshot.lastAction)
        assertEquals(ExecutionResult.Success, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
        assertEquals(1, fixture.executionLogRepository.logs.size)
    }

    @Test
    fun failedProbeKeepsDetailReason() = runTest {
        val fixture = fixture(
            probeResult = executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = null,
                executed = false,
                exitCode = null,
                summary = "没有可用于读取移动数据状态的执行器",
            ),
        )

        fixture.syncer.probeCurrentNetworkControlState()

        assertEquals(ExecutionResult.Failed, fixture.runtimeStatusRepository.currentSnapshot.lastActionResult)
        assertEquals(
            "移动数据状态探测：没有可用于读取移动数据状态的执行器 · 执行器：Root",
            fixture.runtimeStatusRepository.currentSnapshot.lastActionReason,
        )
    }

    private fun fixture(probeResult: com.gaozay.smartflight.executor.ExecutorCommandResult): Fixture {
        val runtimeStatusRepository = FakeRuntimeStatusRepository()
        val executionLogRepository = FakeExecutionLogRepository()
        val probe = FakeNetworkControlProbe().apply {
            currentProbeResult = probeResult
        }
        val formatter = AccessResultFormatter()
        return Fixture(
            runtimeStatusRepository = runtimeStatusRepository,
            executionLogRepository = executionLogRepository,
            syncer = NetworkControlStateSyncer(
                networkControlProbe = probe,
                formatter = formatter,
                snapshotUpdater = AccessRuntimeSnapshotUpdater(runtimeStatusRepository, formatter),
                logWriter = AccessExecutionLogWriter(executionLogRepository, runtimeStatusRepository),
            ),
        )
    }

    private data class Fixture(
        val runtimeStatusRepository: FakeRuntimeStatusRepository,
        val executionLogRepository: FakeExecutionLogRepository,
        val syncer: NetworkControlStateSyncer,
    )
}
