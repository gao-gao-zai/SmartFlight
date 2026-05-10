package com.gaozay.smartflight.executor

import android.Manifest
import com.gaozay.smartflight.domain.model.ExecutorType

data class ExecutorCommand(
    val rawCommand: String,
    val purpose: String,
)

object ExecutorReadonlyCommands {
    val ReadAirplaneModeState = ExecutorCommand(
        rawCommand = "settings get global airplane_mode_on",
        purpose = "读取飞行模式状态",
    )
}

object ExecutorWriteCommands {
    fun setAirplaneModeState(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = "settings put global airplane_mode_on ${if (enabled) "1" else "0"}",
        purpose = if (enabled) "开启飞行模式" else "关闭飞行模式",
    )

    fun grantUsageStatsAccess(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "cmd appops set $packageName android:get_usage_stats allow",
        purpose = "授予使用情况访问权限",
    )

    fun grantNotificationPermission(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}",
        purpose = "授予通知权限",
    )

    fun whitelistBatteryOptimization(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "dumpsys deviceidle whitelist +$packageName",
        purpose = "加入电池优化白名单",
    )
}

data class ExecutorCommandResult(
    val executorType: ExecutorType,
    val executed: Boolean,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val summary: String,
)
