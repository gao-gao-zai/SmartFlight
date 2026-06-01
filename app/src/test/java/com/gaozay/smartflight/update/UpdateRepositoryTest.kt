package com.gaozay.smartflight.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    @Test
    fun giteeSuccessDoesNotRequestGithub() = runTest {
        val apiClient = FakeReleaseApiClient(
            gitee = Result.success(release(UpdateSource.Gitee, "v0.0.17")),
            github = Result.success(release(UpdateSource.GitHub, "v0.0.18")),
        )
        val repository = DefaultUpdateRepository(apiClient, VersionComparator())

        val result = repository.checkForUpdates("0.0.16", skippedUpdateVersion = null, manual = false)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(listOf(UpdateSource.Gitee), apiClient.requests)
    }

    @Test
    fun giteeFailureFallsBackToGithub() = runTest {
        val apiClient = FakeReleaseApiClient(
            gitee = Result.failure(IllegalStateException("gitee down")),
            github = Result.success(release(UpdateSource.GitHub, "v0.0.17")),
        )
        val repository = DefaultUpdateRepository(apiClient, VersionComparator())

        val result = repository.checkForUpdates("0.0.16", skippedUpdateVersion = null, manual = false)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(UpdateSource.GitHub, (result as UpdateCheckResult.UpdateAvailable).release.source)
        assertEquals(listOf(UpdateSource.Gitee, UpdateSource.GitHub), apiClient.requests)
    }

    @Test
    fun currentVersionLatestReturnsUpToDate() = runTest {
        val repository = DefaultUpdateRepository(
            FakeReleaseApiClient(gitee = Result.success(release(UpdateSource.Gitee, "v0.0.16"))),
            VersionComparator(),
        )

        val result = repository.checkForUpdates("0.0.16", skippedUpdateVersion = null, manual = true)

        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun skippedLatestVersionSuppressesAutomaticPrompt() = runTest {
        val repository = DefaultUpdateRepository(
            FakeReleaseApiClient(gitee = Result.success(release(UpdateSource.Gitee, "v0.0.17"))),
            VersionComparator(),
        )

        val result = repository.checkForUpdates("0.0.16", skippedUpdateVersion = "0.0.17", manual = false)

        assertTrue(result is UpdateCheckResult.Skipped)
    }

    @Test
    fun manualCheckIgnoresSkippedVersion() = runTest {
        val repository = DefaultUpdateRepository(
            FakeReleaseApiClient(gitee = Result.success(release(UpdateSource.Gitee, "v0.0.17"))),
            VersionComparator(),
        )

        val result = repository.checkForUpdates("0.0.16", skippedUpdateVersion = "v0.0.17", manual = true)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
    }

    private fun release(source: UpdateSource, tag: String): ReleaseInfo = ReleaseInfo(
        source = source,
        tagName = tag,
        name = tag,
        body = "notes",
        htmlUrl = "${source.releasesUrl}/tag/$tag",
    )

    private class FakeReleaseApiClient(
        private val gitee: Result<ReleaseInfo>,
        private val github: Result<ReleaseInfo> = Result.failure(IllegalStateException("unexpected github call")),
    ) : ReleaseApiClient {
        val requests = mutableListOf<UpdateSource>()

        override suspend fun fetchLatest(source: UpdateSource): Result<ReleaseInfo> {
            requests += source
            return when (source) {
                UpdateSource.Gitee -> gitee
                UpdateSource.GitHub -> github
            }
        }
    }
}
