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
            title = "Usage access",
            available = granted,
            summary = if (granted) "Usage access granted" else "Usage access not granted",
            recommendation = if (granted) {
                "Foreground app detection can be enabled."
            } else {
                "Open system settings and grant usage access to SmartFlight."
            },
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
            title = "Notification permission",
            available = granted,
            summary = if (granted) "Notifications available" else "Notifications disabled or not granted",
            recommendation = if (granted) {
                "Foreground service notifications can be shown."
            } else {
                "Grant notification permission before enabling the foreground service."
            },
        )
    }

    fun checkBatteryOptimization(): AccessCheckResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        return AccessCheckResult(
            title = "Battery optimization",
            available = ignored,
            summary = if (ignored) "Battery optimization ignored" else "Battery optimization still active",
            recommendation = if (ignored) {
                "Background survival is less likely to be restricted by battery optimization."
            } else {
                "Allow SmartFlight to ignore battery optimization for better background reliability."
            },
        )
    }
}
