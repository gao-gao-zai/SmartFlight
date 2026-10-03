package com.gaozay.smartflight.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps WHERE isInstalled = 1 ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps")
    suspend fun getAll(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): InstalledAppEntity?

    @Upsert
    suspend fun upsert(app: InstalledAppEntity)

    @Upsert
    suspend fun upsertAll(apps: List<InstalledAppEntity>)

    @Transaction
    suspend fun replaceScannedApps(apps: List<InstalledAppEntity>) {
        upsertAll(apps)
        if (apps.isNotEmpty()) {
            markMissingUninstalled(apps.map { it.packageName })
        } else {
            markAllUninstalled()
        }
    }

    // Keep saved manual choices for a later reinstall, but exclude archived rows from runtime/UI.
    @Query("UPDATE installed_apps SET isInstalled = 0 WHERE packageName NOT IN (:packageNames)")
    suspend fun markMissingUninstalled(packageNames: List<String>)

    @Query("UPDATE installed_apps SET isInstalled = 0")
    suspend fun markAllUninstalled()

    @Query("UPDATE installed_apps SET isInstalled = 0 WHERE packageName = :packageName")
    suspend fun markUninstalled(packageName: String)

    @Query("SELECT COUNT(*) FROM installed_apps WHERE isInstalled = 1")
    fun observeCount(): Flow<Int>
}
