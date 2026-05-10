package com.gaozay.smartflight.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val iconCacheKey: String?,
    val isSystemApp: Boolean,
    val hasLauncherEntry: Boolean,
    val declaresInternetPermission: Boolean,
    val isAutoDetectedOnline: Boolean,
    val isInOnlineList: Boolean,
    val isInWhitelist: Boolean,
    val isInBlacklist: Boolean,
    val lastScannedAtMillis: Long,
)
