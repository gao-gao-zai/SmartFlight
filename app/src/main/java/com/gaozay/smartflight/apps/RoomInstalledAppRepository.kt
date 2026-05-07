package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppListStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInstalledAppRepository @Inject constructor(
    private val installedAppDao: InstalledAppDao,
    private val installedAppScanner: InstalledAppScanner,
) : InstalledAppRepository {
    override fun observeApps(): Flow<List<InstalledAppEntity>> = installedAppDao.observeAll()

    override fun observeAppsByStatus(status: AppListStatus): Flow<List<InstalledAppEntity>> =
        installedAppDao.observeByListStatus(status.name)

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
                scanned.copy(listStatus = existing.listStatus)
            }
        }
        installedAppDao.replaceScannedApps(scannedApps)
        return scannedApps.size
    }

    override suspend fun upsertApps(apps: List<InstalledAppEntity>) {
        installedAppDao.upsertAll(apps)
    }

    override suspend fun setListStatus(packageName: String, status: AppListStatus) {
        installedAppDao.updateListStatus(
            packageName = packageName,
            listStatus = status.name,
        )
    }
}
