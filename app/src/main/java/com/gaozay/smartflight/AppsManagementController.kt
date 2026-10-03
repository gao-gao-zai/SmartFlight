package com.gaozay.smartflight

import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppFilterState
import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.InternetPermissionFilter
import com.gaozay.smartflight.apps.LauncherFilter
import com.gaozay.smartflight.i18n.AppStrings
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

@ViewModelScoped
class AppsManagementController @Inject constructor(
    private val installedAppRepository: InstalledAppRepository,
) {
    private val appFilter = MutableStateFlow(AppFilter.All)
    private val appInternetPermissionFilter = MutableStateFlow(InternetPermissionFilter.All)
    private val appTypeFilter = MutableStateFlow(AppTypeFilter.User)
    private val appLauncherFilter = MutableStateFlow(LauncherFilter.All)

    val appQuery = MutableStateFlow("")
    val appScanning = MutableStateFlow(false)
    val appLastScanSummary = MutableStateFlow(AppStrings.get(R.string.not_scanned_yet))
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
            appLastScanSummary.value = AppStrings.get(R.string.scan_failed, it.message ?: AppStrings.get(R.string.unknown_error))
            appScanning.value = false
            return
        }
        appLastScanSummary.value = AppStrings.quantity(R.plurals.the_last_scan_found_user_apps, (count).toInt(), count)
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
