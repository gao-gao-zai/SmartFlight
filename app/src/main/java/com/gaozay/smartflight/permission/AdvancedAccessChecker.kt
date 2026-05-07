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
            summary = if (suExists) "已检测到 Root 二进制文件" else "未检测到 Root 二进制文件",
            recommendation = if (suExists) {
                "后续接入 Root 执行器后，需要在授权弹窗中允许 SmartFlight。"
            } else {
                "如果设备没有 Root，请优先使用 Shizuku 或 ADB 初始化方案。"
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
            summary = if (installed) "已检测到 Shizuku 应用" else "未检测到 Shizuku 应用",
            recommendation = if (installed) {
                "请先启动 Shizuku。后续接入执行器后，在 Shizuku 授权弹窗中允许 SmartFlight。"
            } else {
                "推荐安装并启动 Shizuku，这是非 Root 设备上更稳定的高级权限方案。"
            },
        )
    }

    private fun checkAdbBootstrapped(): AccessCheckResult {
        return AccessCheckResult(
            title = "ADB 初始化",
            available = false,
            summary = "尚未完成 ADB 初始化",
            recommendation = "教程：1. 打开开发者选项；2. 打开 USB 调试；3. 电脑连接设备并执行 adb devices，确认设备为 device；4. 后续会在这里提供一键初始化命令，用于授予 SmartFlight 所需的高级控制能力。",
        )
    }
}
