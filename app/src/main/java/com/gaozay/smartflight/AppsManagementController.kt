package com.gaozay.smartflight

import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppFilterState
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class AppsManagementController @Inject constructor(
    private val installedAppRepository: InstalledAppRepository,
) {
    private val appFilter = MutableStateFlow(AppFilter.All)
    private val appInternetPermissionFilter = MutableStateFlow(InternetPermissionFilter.All)
    private val appTypeFilter = MutableStateFlow(AppTypeFilter.User)
    private val appLauncherFilter = MutableStateFlow(LauncherFilter.All)

    val appQuery = MutableStateFlow("")
    val appScanning = MutableStateFlow(false)
    val appLastScanSummary = MutableStateFlow("尚未扫描")
    internal fun appFilterStateFlow() = combine(
        appFilter.asStateFlow(),
        appInternetPermissionFilter.asStateFlow(),
        appTypeFilter.asStateFlow(),
        appLauncherFilter.asStateFlow(),
    ) { filter, internetPermissionFilter, typeFilter, launcherFilter ->
        AppFilterState(
            filter = filter,
            internetPermissionFilter = internetPermissionFilter,
            typeFilter = typeFilter,
            launcherFilter = launcherFilter,
        )
    }

    fun updateAppQuery(query: String) {
        appQuery.value = query
    }

    fun updateAppFilter(filter: AppFilter) {
        appFilter.value = filter
    }

    fun updateAppInternetPermissionFilter(filter: InternetPermissionFilter) {
        appInternetPermissionFilter.value = filter
    }

    fun updateAppTypeFilter(filter: AppTypeFilter) {
        appTypeFilter.value = filter
    }

    fun updateAppLauncherFilter(filter: LauncherFilter) {
        appLauncherFilter.value = filter
    }

    fun clearAppAdvancedFilters() {
        appInternetPermissionFilter.value = InternetPermissionFilter.All
        appTypeFilter.value = AppTypeFilter.User
        appLauncherFilter.value = LauncherFilter.All
    }

    suspend fun refreshInstalledApps() {
        appScanning.value = true
        val count = runCatching {
            installedAppRepository.refreshInstalledApps()
        }.getOrElse {
            appLastScanSummary.value = "扫描失败：${it.message ?: "未知错误"}"
            appScanning.value = false
            return
        }
        appLastScanSummary.value = "上次扫描发现 $count 个用户应用"
        appScanning.value = false
    }

    suspend fun setAppManualOnline(packageName: String) {
        installedAppRepository.setManualOnline(packageName)
    }

    suspend fun setAppManualOffline(packageName: String) {
        installedAppRepository.setManualOffline(packageName)
    }

    suspend fun resetAppToDefault(packageName: String) {
        installedAppRepository.resetToDefault(packageName)
    }
}
