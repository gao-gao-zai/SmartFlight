package com.gaozay.smartflight.permission

import android.util.Log
import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RootAccessChecker @Inject constructor(
    private val rootAccessProbeRepository: RootAccessProbeRepository,
) {
    suspend fun check(): AccessCheckResult = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        Log.d(LOG_TAG, "check suPath=${suPath ?: "<none>"}")
        if (suPath == null) {
            return@withContext AccessCheckResult(
                kind = AccessKind.Root,
                title = "Root",
                status = AccessCheckStatus.Missing,
                summary = AppStrings.get(R.string.root_binary_not_detected),
                recommendation = AppStrings.get(R.string.if_the_device_is_not_rooted_use_shizuku_or_adb_initialization_first),
                isBlocking = true,
                actionType = AccessActionType.Refresh,
            )
        }

        val snapshot = rootAccessProbeRepository.getSnapshot()
        val confirmed = snapshot.confirmedAvailable
        AccessCheckResult(
            kind = AccessKind.Root,
            title = "Root",
            status = if (confirmed) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = if (confirmed) AppStrings.get(R.string.root_authorization_confirmed_as_available) else AppStrings.get(R.string.root_binary_detected),
            recommendation = if (confirmed) {
                AppStrings.get(R.string.root_verification_completed_description)
            } else {
                AppStrings.get(R.string.root_verification_recommendation)
            },
            isBlocking = true,
            actionType = if (confirmed) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append(AppStrings.get(R.string.detected_path))
                append(suPath)
                if (snapshot.lastProbeAtMillis > 0) {
                    append(AppStrings.get(R.string.last_test))
                    append(snapshot.lastProbeSummary)
                } else if (!confirmed) {
                    append(AppStrings.get(R.string.no_active_root_authorization_test_has_been_run_yet_root_access_checker))
                }
            },
            satisfiesRequirement = confirmed,
        )
    }

    suspend fun probeAuthorization(): AccessCheckResult = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        Log.d(LOG_TAG, "probeAuthorization suPath=${suPath ?: "<none>"}")
        if (suPath == null) {
            val result = AccessCheckResult(
                kind = AccessKind.Root,
                title = "Root",
                status = AccessCheckStatus.Missing,
                summary = AppStrings.get(R.string.root_binary_not_detected),
                recommendation = AppStrings.get(R.string.root_entry_unavailable_description),
                isBlocking = true,
                actionType = AccessActionType.Refresh,
            )
            rootAccessProbeRepository.updateSnapshot(
                RootProbeSnapshot(
                    confirmedAvailable = false,
                    lastProbeAtMillis = System.currentTimeMillis(),
                    lastProbeSummary = result.summary,
                ),
            )
            return@withContext result
        }

        val probe = runCatching {
            val process = ProcessBuilder(suPath, "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(1800, TimeUnit.MILLISECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!finished) {
                process.destroy()
            }
            RootCommandResult(
                finished = finished,
                exitCode = if (finished) process.exitValue() else null,
                output = output,
            )
        }.getOrNull()
        Log.d(
            LOG_TAG,
            "probeAuthorization result finished=${probe?.finished} exit=${probe?.exitCode} output=${probe?.output?.lineSequence()?.firstOrNull() ?: "<none>"}",
        )

        val confirmed = probe?.finished == true &&
            probe.exitCode == 0 &&
            probe.output.contains("uid=0")
        val summary = when {
            confirmed -> AppStrings.get(R.string.root_authorization_test_succeeded)
            probe == null -> AppStrings.get(R.string.root_authorization_test_failed_unable_to_start_the_su_process)
            probe.finished != true -> AppStrings.get(R.string.root_authorization_test_timeout)
            else -> AppStrings.get(R.string.root_authorization_test_failed_with_exit_code, probe.exitCode ?: -1)
        }

        rootAccessProbeRepository.updateSnapshot(
            RootProbeSnapshot(
                confirmedAvailable = confirmed,
                lastProbeAtMillis = System.currentTimeMillis(),
                lastProbeSummary = summary,
            ),
        )

        AccessCheckResult(
            kind = AccessKind.Root,
            title = "Root",
            status = if (confirmed) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = summary,
            recommendation = if (confirmed) {
                AppStrings.get(R.string.root_shell_confirmed_description)
            } else {
                AppStrings.get(R.string.root_authorization_denied_recommendation)
            },
            isBlocking = true,
            actionType = if (confirmed) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append(AppStrings.get(R.string.command))
                append(suPath)
                append(" -c id")
                if (!probe?.output.isNullOrBlank()) {
                    append(AppStrings.get(R.string.output_root_access_checker))
                    append(probe?.output?.lineSequence()?.firstOrNull())
                }
            },
            satisfiesRequirement = confirmed,
        )
    }

    private fun findSuPath(): String? {
        val knownPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
        )
        knownPaths.firstOrNull { File(it).exists() }?.let {
            Log.d(LOG_TAG, "findSuPath matched known path=$it")
            return it
        }

        commandSuPath("command -v su")?.let {
            Log.d(LOG_TAG, "findSuPath matched command -v path=$it")
            return it
        }
        commandSuPath("which su")?.let {
            Log.d(LOG_TAG, "findSuPath matched which path=$it")
            return it
        }

        Log.d(LOG_TAG, "findSuPath no match knownPaths=${knownPaths.joinToString()}")
        return null
    }

    private fun commandSuPath(command: String): String? = runCatching {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(1200, TimeUnit.MILLISECONDS)
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!finished) {
            process.destroy()
            return@runCatching null
        }
        output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("/") && it.endsWith("su") }
    }.getOrNull()

    private companion object {
        const val LOG_TAG = "SmartFlightRoot"
    }
}

private data class RootCommandResult(
    val finished: Boolean,
    val exitCode: Int?,
    val output: String,
)
