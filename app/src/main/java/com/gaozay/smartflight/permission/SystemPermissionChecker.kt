package com.gaozay.smartflight.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.runtime.SmartFlightAccessibilityService
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
            kind = AccessKind.UsageStats,
            title = AppStrings.get(R.string.usage_access_permission),
            status = if (granted) AccessCheckStatus.Granted else AccessCheckStatus.Missing,
            summary = if (granted) AppStrings.get(R.string.usage_access_granted) else AppStrings.get(R.string.usage_access_not_granted),
            recommendation = if (granted) {
                AppStrings.get(R.string.the_basic_requirements_for_foreground_app_detection_are_met)
            } else {
                AppStrings.get(R.string.find_smartflight_in_system_settings_and_enable_usage_access)
            },
            isBlocking = true,
            actionType = AccessActionType.OpenSettings,
        )
    }

    fun checkAccessibilityAccess(): AccessCheckResult {
        val granted = isAccessibilityServiceEnabled()
        return AccessCheckResult(
            kind = AccessKind.Accessibility,
            title = AppStrings.get(R.string.accessibility_foreground_monitoring),
            status = if (granted) AccessCheckStatus.Granted else AccessCheckStatus.Missing,
            summary = if (granted) AppStrings.get(R.string.accessibility_foreground_monitoring_enabled) else AppStrings.get(R.string.accessibility_foreground_monitoring_not_enabled),
            recommendation = if (granted) {
                AppStrings.get(R.string.accessibility_monitor_description)
            } else {
                AppStrings.get(R.string.accessibility_system_settings_recommendation)
            },
            isBlocking = true,
            actionType = AccessActionType.OpenSettings,
            detail = AppStrings.get(R.string.accessibility_privacy_description),
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
            kind = AccessKind.Notifications,
            title = AppStrings.get(R.string.notification_permission),
            status = if (granted) AccessCheckStatus.Granted else AccessCheckStatus.Missing,
            summary = if (granted) AppStrings.get(R.string.notification_permission_available) else AppStrings.get(R.string.notifications_disabled_or_permission_not_granted),
            recommendation = if (granted) {
                AppStrings.get(R.string.the_foreground_service_can_display_its_running_notification)
            } else {
                AppStrings.get(R.string.notification_grant_recommendation)
            },
            isBlocking = false,
            actionType = AccessActionType.OpenSettings,
        )
    }

    fun checkBatteryOptimization(): AccessCheckResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        return AccessCheckResult(
            kind = AccessKind.BatteryOptimization,
            title = AppStrings.get(R.string.battery_optimization),
            status = if (ignored) AccessCheckStatus.Ready else AccessCheckStatus.Missing,
            summary = if (ignored) AppStrings.get(R.string.battery_optimization_ignored) else AppStrings.get(R.string.battery_optimization_is_still_restricting_the_app),
            recommendation = if (ignored) {
                AppStrings.get(R.string.battery_optimization_ignored_description)
            } else {
                AppStrings.get(R.string.battery_optimization_ignore_recommendation)
            },
            isBlocking = false,
            actionType = AccessActionType.OpenSettings,
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, SmartFlightAccessibilityService::class.java)
            .flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val service = splitter.next()
            if (service.equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
