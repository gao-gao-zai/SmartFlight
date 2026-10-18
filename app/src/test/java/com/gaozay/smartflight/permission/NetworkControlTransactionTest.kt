package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkControlTransactionTest : LocalizedStringsTest() {
    @Test fun passiveReadCannotOverwriteNewerActionAndBothPublishUnderSharedTransaction() = runTest {
        val status = FakeRuntimeStatusRepository(RuntimeSnapshot(isMobileDataEnabled = true))
        val settings = FakeSettingsRepository(UserSettings(networkControlMode = NetworkControlMode.MobileData))
        val logs = FakeExecutionLogRepository()
        val fake = FakeNetworkControlProbe()
        val releaseRead = CompletableDeferred<Unit>()
        val probe = object : NetworkControlProbe by fake {
            override suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult {
                val captured = executorResult(NetworkControlMode.MobileData, true)
                releaseRead.await()
                return captured
            }
        }
        val formatter = AccessResultFormatter()
        val updater = AccessRuntimeSnapshotUpdater(status, formatter)
        val writer = AccessExecutionLogWriter(logs, status)
        val transaction = NetworkControlTransaction()
        val syncer = NetworkControlStateSyncer(probe, formatter, updater, writer, transaction)
        val action = NetworkControlActionExecutor(settings, status, probe, formatter, updater, writer, transaction)
        val read = launch { syncer.syncCurrentNetworkControlState() }
        runCurrent()
        val write = launch { action.setDisconnectedState(true, TriggerSource.Manual, "test") }
        runCurrent()
        assertTrue(fake.setNetworkControlEnabledRequests.isEmpty())
        releaseRead.complete(Unit)
        read.join(); write.join()
        assertEquals(false, status.currentSnapshot.isMobileDataEnabled)
        assertEquals(listOf(NetworkControlMode.MobileData to false), fake.setNetworkControlEnabledRequests)
    }
}
