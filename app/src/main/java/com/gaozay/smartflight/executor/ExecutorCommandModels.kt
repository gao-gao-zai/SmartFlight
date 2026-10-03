package com.gaozay.smartflight.executor

import android.Manifest
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.i18n.AppStrings

data class ExecutorCommand(
    val rawCommand: String,
    val purpose: String,
)

object ExecutorReadonlyCommands {
    val CheckPhoneService get() = ExecutorCommand(
        rawCommand = "service check phone",
        purpose = AppStrings.get(R.string.check_mobile_data_control_service),
    )

    val ReadAirplaneModeState get() = ExecutorCommand(
        rawCommand = "settings get global airplane_mode_on",
        purpose = AppStrings.get(R.string.read_airplane_mode_state),
    )

    val ReadMobileDataState get() = ExecutorCommand(
        rawCommand = "settings get global mobile_data",
        purpose = AppStrings.get(R.string.read_mobile_data_state),
    )
}

object ExecutorWriteCommands {
    fun setAirplaneModeState(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = buildString {
            append("cmd connectivity airplane-mode ")
            append(if (enabled) "enable" else "disable")
            append(" || (settings put global airplane_mode_on ")
            append(if (enabled) "1" else "0")
            append(" && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ")
            append(if (enabled) "true" else "false")
            append(")")
        },
        purpose = if (enabled) AppStrings.get(R.string.enable_airplane_mode) else AppStrings.get(R.string.disable_airplane_mode),
    )

    fun setMobileDataEnabled(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = "svc data ${if (enabled) "enable" else "disable"}",
        purpose = if (enabled) AppStrings.get(R.string.enable_mobile_data) else AppStrings.get(R.string.disable_mobile_data),
    )

    fun grantUsageStatsAccess(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "cmd appops set $packageName android:get_usage_stats allow",
        purpose = AppStrings.get(R.string.grant_usage_access),
    )

    fun grantNotificationPermission(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}",
        purpose = AppStrings.get(R.string.grant_notification_permission),
    )

    fun whitelistBatteryOptimization(packageName: String): ExecutorCommand = ExecutorCommand(
        rawCommand = "dumpsys deviceidle whitelist +$packageName",
        purpose = AppStrings.get(R.string.allow_unrestricted_battery_usage),
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
    val alreadyInRequestedState: Boolean = false,
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
