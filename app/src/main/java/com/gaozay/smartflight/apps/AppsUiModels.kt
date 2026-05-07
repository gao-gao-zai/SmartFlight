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

data class AppsUiState(
    val apps: List<InstalledAppEntity> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
    val totalCount: Int = 0,
    val candidateCount: Int = 0,
    val whitelistCount: Int = 0,
    val blacklistCount: Int = 0,
    val ignoredCount: Int = 0,
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
}

fun InstalledAppEntity.status(): AppListStatus = runCatching {
    AppListStatus.valueOf(listStatus)
}.getOrDefault(AppListStatus.Candidate)
