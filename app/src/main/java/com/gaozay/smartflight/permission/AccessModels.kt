package com.gaozay.smartflight.permission

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.i18n.ResourceLabel

enum class AccessCheckStatus(override val labelRes: Int) : ResourceLabel {
    Unknown(R.string.not_checked_yet),
    Missing(R.string.requirement_not_met),
    Detected(R.string.detected),
    Granted(R.string.permission_granted),
    Ready(R.string.ready),
}

enum class AccessActionType {
    None,
    OpenSettings,
    RequestPermission,
    Refresh,
}

enum class AccessKind {
    UsageStats, Accessibility, Notifications, BatteryOptimization, Shizuku, Root, Adb, Other,
}

data class AccessCheckResult(
    val title: String,
    val status: AccessCheckStatus,
    val summary: String,
    val recommendation: String,
    val isBlocking: Boolean,
    val actionType: AccessActionType = AccessActionType.None,
    val detail: String? = null,
    val copyText: String? = null,
    val copyLabel: String? = null,
    val satisfiesRequirement: Boolean = status == AccessCheckStatus.Granted ||
        status == AccessCheckStatus.Ready,
    val kind: AccessKind = AccessKind.Other,
) {
    val statusLabel: String get() = status.label
}

data class AdvancedAccessState(
    val selectedExecutorType: ExecutorType = ExecutorType.Unavailable,
    val checks: List<AccessCheckResult> = emptyList(),
) {
    val isAvailable: Boolean = checks.any { it.satisfiesRequirement }
    val blockingIssues: List<AccessCheckResult> = checks.filter { it.isBlocking && !it.satisfiesRequirement }
    val gatingIssues: List<AccessCheckResult> = if (isAvailable) emptyList() else blockingIssues
}

data class AccessGateState(
    val advancedAccess: AdvancedAccessState = AdvancedAccessState(),
    val usageStatsAccess: AccessCheckResult = AccessCheckResult(
        kind = AccessKind.UsageStats,
        title = AppStrings.get(R.string.usage_access_permission),
        status = AccessCheckStatus.Unknown,
        summary = AppStrings.get(R.string.not_checked_yet),
        recommendation = AppStrings.get(R.string.smartflight_needs_usage_access_to_identify_the_current_foreground_app),
        isBlocking = true,
        actionType = AccessActionType.OpenSettings,
    ),
    val accessibilityAccess: AccessCheckResult = AccessCheckResult(
        kind = AccessKind.Accessibility,
        title = AppStrings.get(R.string.accessibility_foreground_monitoring),
        status = AccessCheckStatus.Unknown,
        summary = AppStrings.get(R.string.not_checked_yet),
        recommendation = AppStrings.get(R.string.accessibility_enable_recommendation),
        isBlocking = true,
        actionType = AccessActionType.OpenSettings,
    ),
    val notificationAccess: AccessCheckResult = AccessCheckResult(
        kind = AccessKind.Notifications,
        title = AppStrings.get(R.string.notification_permission),
        status = AccessCheckStatus.Unknown,
        summary = AppStrings.get(R.string.not_checked_yet),
        recommendation = AppStrings.get(R.string.notification_permission_recommendation),
        isBlocking = false,
        actionType = AccessActionType.OpenSettings,
    ),
    val batteryOptimization: AccessCheckResult = AccessCheckResult(
        kind = AccessKind.BatteryOptimization,
        title = AppStrings.get(R.string.battery_optimization),
        status = AccessCheckStatus.Unknown,
        summary = AppStrings.get(R.string.not_checked_yet),
        recommendation = AppStrings.get(R.string.battery_optimization_recommendation),
        isBlocking = false,
        actionType = AccessActionType.OpenSettings,
    ),
    val lastCheckedAtMillis: Long = 0,
) {
    val foregroundDetectionAvailable: Boolean =
        usageStatsAccess.satisfiesRequirement || accessibilityAccess.satisfiesRequirement

    val blockingChecks: List<AccessCheckResult> = buildList {
        if (!foregroundDetectionAvailable) {
            add(accessibilityAccess)
            add(usageStatsAccess)
        }
        addAll(advancedAccess.gatingIssues)
    }
    val advisoryChecks: List<AccessCheckResult> = listOf(notificationAccess, batteryOptimization)
        .filterNot { it.satisfiesRequirement }
    val canEnterApp: Boolean = blockingChecks.isEmpty()
}
