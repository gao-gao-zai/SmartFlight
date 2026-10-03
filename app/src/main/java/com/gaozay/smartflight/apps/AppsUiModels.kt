package com.gaozay.smartflight.apps

import com.gaozay.smartflight.R
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.i18n.ResourceLabel

enum class AppFilter(override val labelRes: Int) : ResourceLabel {
    All(R.string.all_apps_ui_models),
    Online(R.string.online_apps_ui_models),
    Offline(R.string.offline_apps_ui_models),
    Whitelist(R.string.whitelist),
    Blacklist(R.string.blacklist),
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

fun InstalledAppEntity.statusLabel(): String = if (isOnline()) AppStrings.get(R.string.online) else AppStrings.get(R.string.offline)
