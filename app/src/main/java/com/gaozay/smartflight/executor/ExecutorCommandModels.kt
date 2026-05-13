package com.gaozay.smartflight.executor

import android.Manifest
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ExecutorType

data class ExecutorCommand(
    val rawCommand: String,
    val purpose: String,
)

object ExecutorReadonlyCommands {
    val CheckPhoneService = ExecutorCommand(
        rawCommand = "service check phone",
        purpose = "检测移动数据控制服务",
    )

    val ReadAirplaneModeState = ExecutorCommand(
        rawCommand = "settings get global airplane_mode_on",
        purpose = "读取飞行模式状态",
    )

    val ReadMobileDataState = ExecutorCommand(
        rawCommand = "settings get global mobile_data",
        purpose = "读取移动数据状态",
    )
}

object ExecutorWriteCommands {
    fun setAirplaneModeState(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = buildString {
            append("settings put global airplane_mode_on ")
            append(if (enabled) "1" else "0")
            append(" && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ")
            append(if (enabled) "true" else "false")
        },
        purpose = if (enabled) "开启飞行模式" else "关闭飞行模式",
    )

    fun setMobileDataEnabled(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = "svc data ${if (enabled) "enable" else "disable"}",
        purpose = if (enabled) "开启移动数据" else "关闭移动数据",
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
    val controlMode: NetworkControlMode? = null,
    val controlledEnabled: Boolean? = null,
    val executed: Boolean,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val summary: String,
)

fun parseBinaryToggleState(stdout: String): Boolean? = when (stdout.trim()) {
    "1" -> true
    "0" -> false
    else -> null
}

fun isPhoneServiceUnavailable(stdout: String, stderr: String): Boolean {
    val combined = buildString {
        if (stdout.isNotBlank()) append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr)
        }
    }
    return combined.contains("Service phone: not found", ignoreCase = true) ||
        combined.contains("Can't find service: phone", ignoreCase = true)
}
