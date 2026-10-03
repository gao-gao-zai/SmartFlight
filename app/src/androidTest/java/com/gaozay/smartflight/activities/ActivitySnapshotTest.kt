package com.gaozay.smartflight.activities

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.runtime.DataStoreRuntimeStatusRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivitySnapshotTest {
    @Test
    fun parallelForegroundAndOtherStatusUpdatesKeepBothFields() = runBlocking {
        val repo = DataStoreRuntimeStatusRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = repo.snapshot.first()
        try {
            repo.updateSnapshot { it.copy(foregroundEventTimestampMillis = 0, pendingScreenOffDisconnectAtMillis = 0) }
            val gate = CompletableDeferred<Unit>()
            val writers = (1..20).flatMap {
                listOf(
                    async(Dispatchers.IO) { gate.await(); repo.updateSnapshot { it.copy(foregroundEventTimestampMillis = it.foregroundEventTimestampMillis + 1) } },
                    async(Dispatchers.IO) { gate.await(); repo.updateSnapshot { it.copy(pendingScreenOffDisconnectAtMillis = (it.pendingScreenOffDisconnectAtMillis ?: 0) + 1) } },
                )
            }
            gate.complete(Unit)
            writers.awaitAll()
            val snapshot = repo.snapshot.first()
            assertEquals(20L, snapshot.foregroundEventTimestampMillis)
            assertEquals(20L, snapshot.pendingScreenOffDisconnectAtMillis)
        } finally { repo.updateSnapshot { original } }
    }
}
