package com.gaozay.smartflight.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun checkUsageStatsAccess(): AccessCheckResult {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        val granted = mode == AppOpsManager.MODE_ALLOWED
        return AccessCheckResult(
            title = "使用情况访问权限",
            status = if (granted) AccessCheckStatus.Granted else AccessCheckStatus.Missing,
            summary = if (granted) "已授予使用情况访问权限" else "未授予使用情况访问权限",
            recommendation = if (granted) {
                "已满足前台应用检测的基础条件。"
            } else {
                "请在系统设置中找到 SmartFlight，并打开使用情况访问权限。"
            },
            isBlocking = true,
            actionType = AccessActionType.OpenSettings,
        )
    }

    fun checkNotificationPermission(): AccessCheckResult {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        }
        return AccessCheckResult(
            title = "通知权限",
            status = if (granted) AccessCheckStatus.Granted else AccessCheckStatus.Missing,
            summary = if (granted) "通知权限可用" else "通知被关闭或未授权",
            recommendation = if (granted) {
                "前台服务可以正常显示运行通知。"
            } else {
                "启用后台监听服务前，建议先授予通知权限。"
            },
            isBlocking = false,
            actionType = AccessActionType.OpenSettings,
        )
    }

    fun checkBatteryOptimization(): AccessCheckResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        return AccessCheckResult(
            title = "电池优化",
            status = if (ignored) AccessCheckStatus.Ready else AccessCheckStatus.Missing,
            summary = if (ignored) "已忽略电池优化" else "电池优化仍在限制应用",
            recommendation = if (ignored) {
                "后台监听被系统限制的概率会降低。"
            } else {
                "建议允许 SmartFlight 忽略电池优化，以提升后台稳定性。"
            },
            isBlocking = false,
            actionType = AccessActionType.OpenSettings,
        )
    }
}
