package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsUiStateMapperTest {
    @Test
    fun defaultFilterShowsUserAppsOnlyAndKeepsGlobalCounts() {
        val state = buildAppsUiState(
            apps = listOf(
                app("com.example.chat", "Chat", isInOnlineList = true),
                app("android.system", "System", isSystemApp = true, isInOnlineList = false),
                app("com.example.notes", "Notes", isInOnlineList = false),
            ),
            query = "",
            filterState = defaultFilterState(),
            isScanning = false,
            lastScanSummary = "done",
        )

        assertEquals(listOf("com.example.chat", "com.example.notes"), state.apps.map { it.packageName })
        assertEquals(3, state.totalCount)
        assertEquals(1, state.onlineCount)
        assertEquals(2, state.offlineCount)
        assertEquals(2, state.filteredCount)
        assertEquals("done", state.lastScanSummary)
    }

    @Test
    fun queryMatchesLabelOrPackageNameIgnoringCase() {
        val apps = listOf(
            app("com.example.flight", "Sky Control"),
            app("com.example.music", "Music"),
            app("org.reader", "Reader"),
        )

        val labelResult = buildAppsUiState(
            apps = apps,
            query = "sky",
            filterState = defaultFilterState(appTypeFilter = AppTypeFilter.All),
            isScanning = false,
            lastScanSummary = "",
        )
        val packageResult = buildAppsUiState(
            apps = apps,
            query = "MUSIC",
            filterState = defaultFilterState(appTypeFilter = AppTypeFilter.All),
            isScanning = false,
            lastScanSummary = "",
        )

        assertEquals(listOf("com.example.flight"), labelResult.apps.map { it.packageName })
        assertEquals(listOf("com.example.music"), packageResult.apps.map { it.packageName })
    }

    @Test
    fun combinesStatusInternetTypeAndLauncherFilters() {
        val state = buildAppsUiState(
            apps = listOf(
                app(
                    packageName = "com.example.target",
                    label = "Target",
                    isInOnlineList = true,
                    declaresInternetPermission = true,
                    hasLauncherEntry = true,
                ),
                app(
                    packageName = "com.example.nointernet",
                    label = "No Internet",
                    isInOnlineList = true,
                    declaresInternetPermission = false,
                    hasLauncherEntry = true,
                ),
                app(
                    packageName = "com.example.nolauncher",
                    label = "No Launcher",
                    isInOnlineList = true,
                    declaresInternetPermission = true,
                    hasLauncherEntry = false,
                ),
                app(
                    packageName = "android.system",
                    label = "System Target",
                    isSystemApp = true,
                    isInOnlineList = true,
                    declaresInternetPermission = true,
                    hasLauncherEntry = true,
                ),
            ),
            query = "",
            filterState = defaultFilterState(
                filter = AppFilter.Online,
                internetPermissionFilter = InternetPermissionFilter.Declared,
                appTypeFilter = AppTypeFilter.User,
                launcherFilter = LauncherFilter.HasLauncher,
            ),
            isScanning = true,
            lastScanSummary = "scanning",
        )

        assertEquals(listOf("com.example.target"), state.apps.map { it.packageName })
        assertEquals(1, state.filteredCount)
        assertTrue(state.isScanning)
    }

    @Test
    fun systemAppsCanBeShownByTypeFilter() {
        val state = buildAppsUiState(
            apps = listOf(
                app("com.example.user", "User App"),
                app("android.system", "System App", isSystemApp = true),
            ),
            query = "",
            filterState = defaultFilterState(appTypeFilter = AppTypeFilter.System),
            isScanning = false,
            lastScanSummary = "",
        )

        assertEquals(listOf("android.system"), state.apps.map { it.packageName })
        assertEquals(2, state.totalCount)
        assertEquals(1, state.filteredCount)
    }

    @Test
    fun whitelistAndBlacklistFiltersUseManualRuleFlags() {
        val apps = listOf(
            app("com.example.white", "White", isInOnlineList = true, isInWhitelist = true),
            app("com.example.black", "Black", isInOnlineList = false, isInBlacklist = true),
            app("com.example.auto", "Auto", isInOnlineList = true, isAutoDetectedOnline = true),
        )

        val whitelistState = buildAppsUiState(
            apps = apps,
            query = "",
            filterState = defaultFilterState(filter = AppFilter.Whitelist),
            isScanning = false,
            lastScanSummary = "",
        )
        val blacklistState = buildAppsUiState(
            apps = apps,
            query = "",
            filterState = defaultFilterState(filter = AppFilter.Blacklist),
            isScanning = false,
            lastScanSummary = "",
        )

        assertEquals(listOf("com.example.white"), whitelistState.apps.map { it.packageName })
        assertEquals(listOf("com.example.black"), blacklistState.apps.map { it.packageName })
        assertEquals(1, whitelistState.whitelistCount)
        assertEquals(1, whitelistState.blacklistCount)
    }

    @Test
    fun activeAdvancedFilterCountComesFromSelectedFilters() {
        val state = buildAppsUiState(
            apps = emptyList(),
            query = "",
            filterState = defaultFilterState(
                internetPermissionFilter = InternetPermissionFilter.NotDeclared,
                appTypeFilter = AppTypeFilter.System,
                launcherFilter = LauncherFilter.NoLauncher,
            ),
            isScanning = false,
            lastScanSummary = "",
        )

        assertEquals(3, state.activeAdvancedFilterCount)
    }

    private fun defaultFilterState(
        filter: AppFilter = AppFilter.All,
        internetPermissionFilter: InternetPermissionFilter = InternetPermissionFilter.All,
        appTypeFilter: AppTypeFilter = AppTypeFilter.User,
        launcherFilter: LauncherFilter = LauncherFilter.All,
    ): AppFilterState = AppFilterState(
        filter = filter,
        internetPermissionFilter = internetPermissionFilter,
        typeFilter = appTypeFilter,
        launcherFilter = launcherFilter,
    )

    private fun app(
        packageName: String,
        label: String,
        isSystemApp: Boolean = false,
        hasLauncherEntry: Boolean = true,
        declaresInternetPermission: Boolean = true,
        isAutoDetectedOnline: Boolean = false,
        isInOnlineList: Boolean = false,
        isInWhitelist: Boolean = false,
        isInBlacklist: Boolean = false,
    ): InstalledAppEntity = InstalledAppEntity(
        packageName = packageName,
        label = label,
        iconCacheKey = null,
        isSystemApp = isSystemApp,
        hasLauncherEntry = hasLauncherEntry,
        declaresInternetPermission = declaresInternetPermission,
        isAutoDetectedOnline = isAutoDetectedOnline,
        isInOnlineList = isInOnlineList,
        isInWhitelist = isInWhitelist,
        isInBlacklist = isInBlacklist,
        lastScannedAtMillis = 1L,
    )
}
