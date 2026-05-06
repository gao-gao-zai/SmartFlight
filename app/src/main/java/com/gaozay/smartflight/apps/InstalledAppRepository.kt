package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppListStatus
import kotlinx.coroutines.flow.Flow

interface InstalledAppRepository {
    fun observeApps(): Flow<List<InstalledAppEntity>>

    fun observeAppsByStatus(status: AppListStatus): Flow<List<InstalledAppEntity>>

    fun observeAppCount(): Flow<Int>

    suspend fun getApp(packageName: String): InstalledAppEntity?

    suspend fun upsertApps(apps: List<InstalledAppEntity>)

    suspend fun setListStatus(packageName: String, status: AppListStatus)
}
