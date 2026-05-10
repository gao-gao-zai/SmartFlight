package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag

enum class AppFilter(val label: String) {
    All("All"),
    Online("Online"),
    Offline("Offline"),
    Whitelist("Whitelist"),
    Blacklist("Blacklist"),
}

enum class InternetPermissionFilter {
    All,
    Declared,
    NotDeclared,
}

enum class AppTypeFilter {
    All,
    User,
    System,
}

enum class LauncherFilter {
    All,
    HasLauncher,
    NoLauncher,
}

data class AppsUiState(
    val apps: List<InstalledAppEntity> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
    val internetPermissionFilter: InternetPermissionFilter = InternetPermissionFilter.All,
    val appTypeFilter: AppTypeFilter = AppTypeFilter.User,
    val launcherFilter: LauncherFilter = LauncherFilter.All,
    val totalCount: Int = 0,
    val onlineCount: Int = 0,
    val offlineCount: Int = 0,
    val whitelistCount: Int = 0,
    val blacklistCount: Int = 0,
    val filteredCount: Int = 0,
    val isScanning: Boolean = false,
    val lastScanSummary: String = "Not scanned yet",
) {
    fun countFor(filter: AppFilter): Int = when (filter) {
        AppFilter.All -> totalCount
        AppFilter.Online -> onlineCount
        AppFilter.Offline -> offlineCount
        AppFilter.Whitelist -> whitelistCount
        AppFilter.Blacklist -> blacklistCount
    }

    val activeAdvancedFilterCount: Int
        get() = listOf(
            internetPermissionFilter != InternetPermissionFilter.All,
            appTypeFilter != AppTypeFilter.User,
            launcherFilter != LauncherFilter.All,
        ).count { it }
}

fun InstalledAppEntity.isOnline(): Boolean = isInOnlineList

fun InstalledAppEntity.sourceTag(): AppOnlineSourceTag? = when {
    isInWhitelist || isInBlacklist -> AppOnlineSourceTag.Manual
    isInOnlineList && isAutoDetectedOnline -> AppOnlineSourceTag.Auto
    else -> null
}

fun InstalledAppEntity.isPureAutoOnline(): Boolean =
    isInOnlineList && isAutoDetectedOnline && !isInWhitelist && !isInBlacklist

fun InstalledAppEntity.statusLabel(): String = if (isOnline()) "联网" else "非联网"
