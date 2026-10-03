package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomInstalledAppRepositoryTest {
    private val dao = MemoryDao()
    private val source = MemorySource()
    private val repository = RoomInstalledAppRepository(dao, source)

    @Test
    fun packageUpdateRefreshesDefaultClassificationWithoutScanningOtherPackages() = runTest {
        dao.upsert(app("one"))
        dao.upsert(app("two"))
        source.apps = listOf(app("one", online = false))
        repository.refreshPackage("one")
        assertFalse(repository.getApp("one")!!.isInOnlineList)
        assertTrue(repository.getApp("two")!!.isInOnlineList)
        assertEquals(listOf("one"), source.targeted)
        assertEquals(0, source.fullScans)
    }

    @Test
    fun whitelistAndBlacklistSurvivePermissionChangesAndReinstall() = runTest {
        for (online in listOf(true, false)) {
            source.apps = listOf(app("one"))
            repository.refreshPackage("one")
            if (online) repository.setManualOnline("one") else repository.setManualOffline("one")
            source.apps = emptyList()
            repository.refreshPackage("one", removeIfMissing = true)
            assertNull(repository.getApp("one"))
            assertEquals(0, repository.observeAppCount().first())
            assertEquals(emptyList<InstalledAppEntity>(), repository.observeApps().first())
            assertFalse(dao.getByPackageName("one")!!.isInstalled)
            source.apps = listOf(app("one", online = !online))
            repository.refreshPackage("one")
            val restored = repository.getApp("one")!!
            assertEquals(online, restored.isInOnlineList)
            assertEquals(online, restored.isInWhitelist)
            assertEquals(!online, restored.isInBlacklist)
            assertEquals(!online, restored.isAutoDetectedOnline)
        }
    }

    @Test
    fun delayedRemovalDoesNotArchiveAPackageAlreadyReinstalled() = runTest {
        dao.upsert(app("one").copy(isInWhitelist = true))
        source.apps = listOf(app("one", online = false))
        repository.refreshPackage("one", removeIfMissing = true)
        assertTrue(repository.getApp("one")!!.isInOnlineList)
    }

    @Test
    fun missingDuringUpdateDoesNotArchiveOrDiscardRules() = runTest {
        dao.upsert(app("one").copy(isInBlacklist = true, isInOnlineList = false))
        repository.refreshPackage("one")
        assertTrue(repository.getApp("one")!!.isInBlacklist)
    }

    @Test
    fun startupReconciliationAddsMissedInstallsArchivesRemovalsAndRestoresRules() = runTest {
        dao.upsert(app("removed"))
        dao.upsert(app("restored").copy(isInstalled = false, isInBlacklist = true, isInOnlineList = false))
        source.apps = listOf(app("new"), app("restored"))
        assertEquals(2, repository.refreshInstalledApps())
        assertNull(repository.getApp("removed"))
        assertFalse(repository.getApp("restored")!!.isInOnlineList)
        assertEquals(setOf("new", "restored"), repository.observeApps().first().map { it.packageName }.toSet())
    }

    @Test
    fun emptySuccessfulScanArchivesAllRowsButFailedScanLeavesThemIntact() = runTest {
        dao.upsert(app("one"))
        source.failure = IllegalStateException("Package manager unavailable")
        assertTrue(runCatching { repository.refreshInstalledApps() }.isFailure)
        assertTrue(repository.getApp("one")!!.isInstalled)
        assertTrue(runCatching { repository.refreshPackage("one", true) }.isFailure)
        assertTrue(repository.getApp("one")!!.isInstalled)
        source.failure = null
        assertEquals(0, repository.refreshInstalledApps())
        assertNull(repository.getApp("one"))
        assertNotNull(dao.getByPackageName("one"))
    }

    @Test
    fun manualChoiceQueuedDuringScanIsNotLost() = runTest {
        dao.upsert(app("one"))
        source.apps = listOf(app("one"))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        dao.beforeGetAll = {
            entered.complete(Unit)
            release.await()
        }
        val scan = async { repository.refreshInstalledApps() }
        entered.await()
        val manual = async(start = CoroutineStart.UNDISPATCHED) { repository.setManualOffline("one") }
        release.complete(Unit)
        scan.await()
        manual.await()
        assertTrue(repository.getApp("one")!!.isInBlacklist)
        assertFalse(repository.getApp("one")!!.isInOnlineList)
    }

    @Test
    fun resetAfterReinstallUsesNewDefault() = runTest {
        dao.upsert(app("one").copy(isInstalled = false, isInWhitelist = true))
        source.apps = listOf(app("one", online = false))
        repository.refreshPackage("one")
        repository.resetToDefault("one")
        assertFalse(repository.getApp("one")!!.isInWhitelist)
        assertFalse(repository.getApp("one")!!.isInOnlineList)
    }

    private fun app(name: String, online: Boolean = true) = InstalledAppEntity(
        name, name, name, false, true, online, online, online, false, false, 1,
    )

    private class MemorySource : InstalledAppSource {
        var apps = emptyList<InstalledAppEntity>()
        var failure: Exception? = null
        var fullScans = 0
        val targeted = mutableListOf<String>()
        override fun scanInstalledApps(): List<InstalledAppEntity> {
            failure?.let { throw it }
            fullScans++
            return apps
        }
        override fun scanPackage(packageName: String): InstalledAppEntity? {
            failure?.let { throw it }
            targeted += packageName
            return apps.find { it.packageName == packageName }
        }
    }

    private class MemoryDao : InstalledAppDao {
        private val rows = MutableStateFlow<Map<String, InstalledAppEntity>>(emptyMap())
        var beforeGetAll: suspend () -> Unit = {}
        override fun observeAll(): Flow<List<InstalledAppEntity>> = rows.map { it.values.filter { app -> app.isInstalled } }
        override fun observeCount(): Flow<Int> = observeAll().map { it.size }
        override suspend fun getAll(): List<InstalledAppEntity> { beforeGetAll(); return rows.value.values.toList() }
        override suspend fun getByPackageName(packageName: String) = rows.value[packageName]
        override suspend fun upsert(app: InstalledAppEntity) { rows.value = rows.value + (app.packageName to app) }
        override suspend fun upsertAll(apps: List<InstalledAppEntity>) { rows.value = rows.value + apps.associateBy { it.packageName } }
        override suspend fun markMissingUninstalled(packageNames: List<String>) {
            rows.value = rows.value.mapValues { (name, app) -> if (name in packageNames) app else app.copy(isInstalled = false) }
        }
        override suspend fun markAllUninstalled() = markMissingUninstalled(emptyList())
        override suspend fun markUninstalled(packageName: String) { rows.value[packageName]?.let { upsert(it.copy(isInstalled = false)) } }
    }
}
