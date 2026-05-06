package com.gaozay.smartflight.permission

import android.content.Context
import android.content.pm.PackageManager
import com.gaozay.smartflight.domain.model.ExecutorType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun check(): AdvancedAccessState {
        val root = checkRoot()
        val shizuku = checkShizuku()
        val adbBootstrapped = checkAdbBootstrapped()
        val checks = listOf(root, shizuku, adbBootstrapped)
        val selectedExecutorType = when {
            root.available -> ExecutorType.Root
            shizuku.available -> ExecutorType.Shizuku
            adbBootstrapped.available -> ExecutorType.AdbBootstrapped
            else -> ExecutorType.Unavailable
        }
        return AdvancedAccessState(
            selectedExecutorType = selectedExecutorType,
            checks = checks,
        )
    }

    private fun checkRoot(): AccessCheckResult {
        val suExists = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
        ).any { File(it).exists() }
        return AccessCheckResult(
            title = "Root",
            available = suExists,
            summary = if (suExists) "Root binary detected" else "Root binary not detected",
            recommendation = if (suExists) {
                "Root execution can be used after authorization is confirmed."
            } else {
                "Use Root only on devices where you intentionally granted root access."
            },
        )
    }

    private fun checkShizuku(): AccessCheckResult {
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        return AccessCheckResult(
            title = "Shizuku",
            available = installed,
            summary = if (installed) "Shizuku app detected" else "Shizuku app not detected",
            recommendation = if (installed) {
                "Start Shizuku and grant SmartFlight access when executor integration is enabled."
            } else {
                "Install and start Shizuku to use the recommended non-root executor path."
            },
        )
    }

    private fun checkAdbBootstrapped(): AccessCheckResult {
        return AccessCheckResult(
            title = "ADB Bootstrapped",
            available = false,
            summary = "ADB initialization state is not configured yet",
            recommendation = "This path will be enabled after the initialization chain is implemented.",
        )
    }
}
