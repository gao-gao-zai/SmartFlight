package com.gaozay.smartflight.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gaozay.smartflight.domain.model.AppListStatus

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val iconCacheKey: String?,
    val isSystemApp: Boolean,
    val hasLauncherEntry: Boolean,
    val declaresInternetPermission: Boolean,
    val isCandidate: Boolean,
    val listStatus: String = AppListStatus.Candidate.name,
    val lastScannedAtMillis: Long,
)
