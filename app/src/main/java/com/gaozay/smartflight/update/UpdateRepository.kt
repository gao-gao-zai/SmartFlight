package com.gaozay.smartflight.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface UpdateRepository {
    suspend fun checkForUpdates(
        currentVersion: String,
        skippedUpdateVersion: String?,
        manual: Boolean,
    ): UpdateCheckResult
}

class DefaultUpdateRepository @Inject constructor(
    private val apiClient: ReleaseApiClient,
    private val versionComparator: VersionComparator,
) : UpdateRepository {
    override suspend fun checkForUpdates(
        currentVersion: String,
        skippedUpdateVersion: String?,
        manual: Boolean,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        val release = sequenceOf(UpdateSource.Gitee, UpdateSource.GitHub)
            .firstNotNullOfOrNull { source ->
                apiClient.fetchLatest(source)
                    .onFailure { failures += "${source.label}: ${it.message ?: it::class.java.simpleName}" }
                    .getOrNull()
            }

        if (release == null) {
            return@withContext UpdateCheckResult.Failed(
                failures.joinToString("；").ifBlank { "无法连接更新源" },
            )
        }
        if (!versionComparator.isRemoteNewer(currentVersion, release.tagName)) {
            return@withContext UpdateCheckResult.UpToDate(release)
        }
        if (!manual && versionComparator.isSameVersion(release.tagName, skippedUpdateVersion)) {
            return@withContext UpdateCheckResult.Skipped(release)
        }
        UpdateCheckResult.UpdateAvailable(release)
    }
}
