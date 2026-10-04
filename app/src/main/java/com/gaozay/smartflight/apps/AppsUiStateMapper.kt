package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

internal data class AppFilterState(
    val filter: AppFilter,
    val internetPermissionFilter: InternetPermissionFilter,
    val typeFilter: AppTypeFilter,
    val launcherFilter: LauncherFilter,
)

internal fun buildAppsUiState(
    apps: List<InstalledAppEntity>,
    query: String,
    filterState: AppFilterState,
    isScanning: Boolean,
    lastScanSummary: String,
    activityRuleSummaries: Map<String, com.gaozay.smartflight.activities.ActivityRuleSummary> = emptyMap(),
): AppsUiState {
    val filteredApps = apps.filter { app ->
        app.matchesQuery(query) &&
            app.matchesStatusFilter(filterState.filter, activityRuleSummaries[app.packageName]?.ruleCount ?: 0) &&
            app.matchesInternetPermissionFilter(filterState.internetPermissionFilter) &&
            app.matchesTypeFilter(filterState.typeFilter) &&
            app.matchesLauncherFilter(filterState.launcherFilter)
    }
    return AppsUiState(
        apps = filteredApps,
        query = query,
        filter = filterState.filter,
        internetPermissionFilter = filterState.internetPermissionFilter,
        appTypeFilter = filterState.typeFilter,
        launcherFilter = filterState.launcherFilter,
        totalCount = apps.size,
        onlineCount = apps.count { it.isOnline() },
        offlineCount = apps.count { !it.isOnline() },
        whitelistCount = apps.count { it.isInWhitelist },
        blacklistCount = apps.count { it.isInBlacklist },
        activityRuleSummaries = activityRuleSummaries,
        activityRuleAppCount = apps.count { (activityRuleSummaries[it.packageName]?.ruleCount ?: 0) > 0 },
        filteredCount = filteredApps.size,
        isScanning = isScanning,
        lastScanSummary = lastScanSummary,
    )
}

private fun InstalledAppEntity.matchesQuery(query: String): Boolean =
    query.isBlank() ||
        label.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true)

private fun InstalledAppEntity.matchesStatusFilter(filter: AppFilter, activityCount: Int): Boolean = when (filter) {
    AppFilter.All -> true
    AppFilter.Online -> isOnline()
    AppFilter.Offline -> !isOnline()
    AppFilter.Whitelist -> isInWhitelist
    AppFilter.Blacklist -> isInBlacklist
    AppFilter.WithActivities -> activityCount > 0
}

private fun InstalledAppEntity.matchesInternetPermissionFilter(filter: InternetPermissionFilter): Boolean =
    when (filter) {
        InternetPermissionFilter.All -> true
        InternetPermissionFilter.Declared -> declaresInternetPermission
        InternetPermissionFilter.NotDeclared -> !declaresInternetPermission
    }

private fun InstalledAppEntity.matchesTypeFilter(filter: AppTypeFilter): Boolean = when (filter) {
    AppTypeFilter.All -> true
    AppTypeFilter.User -> !isSystemApp
    AppTypeFilter.System -> isSystemApp
}

private fun InstalledAppEntity.matchesLauncherFilter(filter: LauncherFilter): Boolean = when (filter) {
    LauncherFilter.All -> true
    LauncherFilter.HasLauncher -> hasLauncherEntry
    LauncherFilter.NoLauncher -> !hasLauncherEntry
}
