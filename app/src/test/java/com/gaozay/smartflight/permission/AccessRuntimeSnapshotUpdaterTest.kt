package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.domain.model.UnifiedNetworkState
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessRuntimeSnapshotUpdaterTest {
    @Test
    fun controlStateSnapshotUpdatesAirplaneAndDerivedNetworkState() {
        val updater = updater()

        val updated = updater.updateControlStateSnapshot(
            snapshot = RuntimeSnapshot(isWifiConnected = false),
            result = executorResult(
                controlMode = NetworkControlMode.AirplaneMode,
                controlledEnabled = true,
            ),
        )

        assertEquals(true, updated.isAirplaneModeEnabled)
        assertEquals(UnifiedNetworkState.Offline, updated.unifiedNetworkState)
    }

    @Test
    fun controlStateSnapshotUpdatesMobileDataAndDerivedNetworkState() {
        val updater = updater()

        val updated = updater.updateControlStateSnapshot(
            snapshot = RuntimeSnapshot(isWifiConnected = false, isAirplaneModeEnabled = false),
            result = executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = true,
            ),
        )

        assertEquals(true, updated.isMobileDataEnabled)
        assertEquals(UnifiedNetworkState.CellularOnly, updated.unifiedNetworkState)
    }

    @Test
    fun passiveAccessSummaryDoesNotOverrideActiveAction() = runTest {
        val repository = FakeRuntimeStatusRepository(
            RuntimeSnapshot(
                lastAction = ExecutionAction.DisconnectNow,
                lastTriggerSource = TriggerSource.Manual,
                lastActionResult = ExecutionResult.Success,
                lastActionReason = "",
            ),
        )
        val updater = AccessRuntimeSnapshotUpdater(repository, AccessResultFormatter())

        updater.updatePassiveAccessSummary(
            AdvancedAccessState(selectedExecutorType = ExecutorType.Root),
        )

        assertEquals(ExecutionAction.DisconnectNow, repository.currentSnapshot.lastAction)
        assertEquals(TriggerSource.ServiceRestored, repository.currentSnapshot.lastTriggerSource)
        assertEquals("", repository.currentSnapshot.lastActionReason)
    }

    private fun updater(): AccessRuntimeSnapshotUpdater =
        AccessRuntimeSnapshotUpdater(FakeRuntimeStatusRepository(), AccessResultFormatter())
}
