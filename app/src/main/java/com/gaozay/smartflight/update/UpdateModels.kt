package com.gaozay.smartflight.update

import androidx.compose.runtime.Immutable

const val GITEE_RELEASES_URL = "https://gitee.com/gaogaozai/SmartFlight/releases"
const val GITHUB_RELEASES_URL = "https://github.com/gao-gao-zai/SmartFlight/releases"

enum class UpdateSource(
    val label: String,
    val latestReleaseUrl: String,
    val releasesUrl: String,
) {
    Gitee(
        label = "Gitee",
        latestReleaseUrl = "https://gitee.com/api/v5/repos/gaogaozai/SmartFlight/releases/latest",
        releasesUrl = GITEE_RELEASES_URL,
    ),
    GitHub(
        label = "GitHub",
        latestReleaseUrl = "https://api.github.com/repos/gao-gao-zai/SmartFlight/releases/latest",
        releasesUrl = GITHUB_RELEASES_URL,
    ),
}

@Immutable
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

@Immutable
data class ReleaseInfo(
    val source: UpdateSource,
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    val pageUrl: String
        get() = htmlUrl.ifBlank { "${source.releasesUrl}/tag/$tagName" }
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val release: ReleaseInfo) : UpdateCheckResult
    data class Skipped(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

@Immutable
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val manual: Boolean) : UpdateUiState
    data class UpToDate(val message: String) : UpdateUiState
    data class UpdateAvailable(val release: ReleaseInfo, val manual: Boolean) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}
