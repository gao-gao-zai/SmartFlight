package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInstalledAppRepository @Inject constructor(
    private val installedAppDao: InstalledAppDao,
    private val installedAppScanner: InstalledAppScanner,
) : InstalledAppRepository {
    override fun observeApps(): Flow<List<InstalledAppEntity>> = installedAppDao.observeAll()

    override fun observeAppCount(): Flow<Int> = installedAppDao.observeCount()

    override suspend fun getApp(packageName: String): InstalledAppEntity? =
        installedAppDao.getByPackageName(packageName)

    override suspend fun refreshInstalledApps(): Int {
        val existingByPackageName = installedAppDao.getAll().associateBy { it.packageName }
        val scannedApps = installedAppScanner.scanInstalledApps().map { scanned ->
            val existing = existingByPackageName[scanned.packageName]
            if (existing == null) {
                scanned
            } else {
                mergeScannedState(scanned = scanned, existing = existing)
            }
        }
        installedAppDao.replaceScannedApps(scannedApps)
        return scannedApps.size
    }

    override suspend fun upsertApps(apps: List<InstalledAppEntity>) {
        installedAppDao.upsertAll(apps)
    }

    override suspend fun setManualOnline(packageName: String) {
        val app = installedAppDao.getByPackageName(packageName) ?: return
        installedAppDao.upsert(
            app.copy(
                isInOnlineList = true,
                isInWhitelist = true,
                isInBlacklist = false,
            ),
        )
    }

    override suspend fun setManualOffline(packageName: String) {
        val app = installedAppDao.getByPackageName(packageName) ?: return
        installedAppDao.upsert(
            app.copy(
                isInOnlineList = false,
                isInWhitelist = false,
                isInBlacklist = true,
            ),
        )
    }

    override suspend fun resetToDefault(packageName: String) {
        val app = installedAppDao.getByPackageName(packageName) ?: return
        val shouldBeOnline = app.isAutoDetectedOnline
        installedAppDao.upsert(
            app.copy(
                isInOnlineList = shouldBeOnline,
                isInWhitelist = false,
                isInBlacklist = false,
            ),
        )
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
