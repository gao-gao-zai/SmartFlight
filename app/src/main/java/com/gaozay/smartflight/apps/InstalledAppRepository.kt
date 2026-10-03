package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow

interface InstalledAppRepository {
    fun observeApps(): Flow<List<InstalledAppEntity>>

    fun observeAppCount(): Flow<Int>

    suspend fun getApp(packageName: String): InstalledAppEntity?

    suspend fun refreshInstalledApps(): Int

    /** Refresh only this package; archive a missing package only for a confirmed removal. */
    suspend fun refreshPackage(packageName: String, removeIfMissing: Boolean = false)

    suspend fun upsertApps(apps: List<InstalledAppEntity>)

    suspend fun setManualOnline(packageName: String)

    suspend fun setManualOffline(packageName: String)

    suspend fun resetToDefault(packageName: String)
}
