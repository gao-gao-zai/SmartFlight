package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInstalledAppRepository @Inject constructor(
    private val installedAppDao: InstalledAppDao,
    private val installedAppScanner: InstalledAppSource,
) : InstalledAppRepository {
    // A scan and its merge/write must not overwrite a concurrent manual choice or package event.
    private val writeMutex = Mutex()

    override fun observeApps(): Flow<List<InstalledAppEntity>> = installedAppDao.observeAll()

    override fun observeAppCount(): Flow<Int> = installedAppDao.observeCount()

    override suspend fun getApp(packageName: String): InstalledAppEntity? =
        installedAppDao.getByPackageName(packageName)?.takeIf { it.isInstalled }

    override suspend fun refreshInstalledApps(): Int = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existingByPackageName = installedAppDao.getAll().associateBy { it.packageName }
            // Finish the scan before marking anything missing. A failed scan leaves saved rows intact.
            val scannedApps = installedAppScanner.scanInstalledApps().map { scanned ->
                existingByPackageName[scanned.packageName]?.let { mergeScannedState(scanned, it) } ?: scanned
            }
            installedAppDao.replaceScannedApps(scannedApps)
            scannedApps.size
        }
    }

    override suspend fun refreshPackage(packageName: String, removeIfMissing: Boolean) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            // A queued removal can arrive after a quick reinstall. Check current package state first.
            val scanned = installedAppScanner.scanPackage(packageName)
            if (scanned != null) {
                val existing = installedAppDao.getByPackageName(packageName)
                installedAppDao.upsert(existing?.let { mergeScannedState(scanned, it) } ?: scanned)
            } else if (removeIfMissing) {
                installedAppDao.markUninstalled(packageName)
            }
        }
    }

    override suspend fun upsertApps(apps: List<InstalledAppEntity>) {
        writeMutex.withLock { installedAppDao.upsertAll(apps) }
    }

    override suspend fun setManualOnline(packageName: String) = updateChoice(packageName) {
        it.copy(isInOnlineList = true, isInWhitelist = true, isInBlacklist = false)
    }

    override suspend fun setManualOffline(packageName: String) = updateChoice(packageName) {
        it.copy(isInOnlineList = false, isInWhitelist = false, isInBlacklist = true)
    }

    override suspend fun resetToDefault(packageName: String) = updateChoice(packageName) {
        it.copy(isInOnlineList = it.isAutoDetectedOnline, isInWhitelist = false, isInBlacklist = false)
    }

    private suspend fun updateChoice(packageName: String, transform: (InstalledAppEntity) -> InstalledAppEntity) {
        writeMutex.withLock {
            val app = installedAppDao.getByPackageName(packageName) ?: return@withLock
            installedAppDao.upsert(transform(app))
        }
    }

    private fun mergeScannedState(
        scanned: InstalledAppEntity,
        existing: InstalledAppEntity,
    ): InstalledAppEntity {
        val isManualOnline = existing.isInWhitelist
        val isManualOffline = existing.isInBlacklist
        return when {
            isManualOnline -> scanned.copy(
                isInOnlineList = true,
                isInWhitelist = true,
                isInBlacklist = false,
            )

            isManualOffline -> scanned.copy(
                isInOnlineList = false,
                isInWhitelist = false,
                isInBlacklist = true,
            )

            scanned.isAutoDetectedOnline -> scanned.copy(
                isInOnlineList = true,
                isInWhitelist = false,
                isInBlacklist = false,
            )

            else -> scanned.copy(
                isInOnlineList = false,
                isInWhitelist = false,
                isInBlacklist = false,
            )
        }
    }
}
