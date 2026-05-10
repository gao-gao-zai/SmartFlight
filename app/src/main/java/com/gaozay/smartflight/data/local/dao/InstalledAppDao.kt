package com.gaozay.smartflight.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps ORDER BY label COLLATE NOCASE ASC")
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
            deleteMissing(apps.map { it.packageName })
        }
    }

    @Query("DELETE FROM installed_apps WHERE packageName NOT IN (:packageNames)")
    suspend fun deleteMissing(packageNames: List<String>)

    @Query("SELECT COUNT(*) FROM installed_apps")
    fun observeCount(): Flow<Int>
}
