package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppListStatus

enum class AppFilter(val label: String) {
    All("All"),
    Candidate("Candidate"),
    Whitelist("Whitelist"),
    Blacklist("Blacklist"),
    Ignored("Ignored"),
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
    val candidateCount: Int = 0,
    val whitelistCount: Int = 0,
    val blacklistCount: Int = 0,
    val ignoredCount: Int = 0,
    val filteredCount: Int = 0,
    val isScanning: Boolean = false,
    val lastScanSummary: String = "Not scanned yet",
) {
    fun countFor(filter: AppFilter): Int = when (filter) {
        AppFilter.All -> totalCount
        AppFilter.Candidate -> candidateCount
        AppFilter.Whitelist -> whitelistCount
        AppFilter.Blacklist -> blacklistCount
        AppFilter.Ignored -> ignoredCount
    }

    val activeAdvancedFilterCount: Int
        get() = listOf(
            internetPermissionFilter != InternetPermissionFilter.All,
            appTypeFilter != AppTypeFilter.User,
            launcherFilter != LauncherFilter.All,
        ).count { it }
}


fun InstalledAppEntity.status(): AppListStatus = runCatching {
    AppListStatus.valueOf(listStatus)
}.getOrDefault(AppListStatus.Candidate)
