package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutorType

enum class AccessCheckStatus(val label: String) {
    Unknown("尚未检测"),
    Missing("未满足"),
    Detected("已检测"),
    Granted("已授权"),
    Ready("可用"),
}

enum class AccessActionType {
    None,
    OpenSettings,
    RequestPermission,
    Refresh,
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
) {
    val statusLabel: String = status.label
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
        title = "使用情况访问权限",
        status = AccessCheckStatus.Unknown,
        summary = "尚未检测",
        recommendation = "授予使用情况访问权限后，SmartFlight 才能判断当前前台应用。",
        isBlocking = true,
        actionType = AccessActionType.OpenSettings,
    ),
    val accessibilityAccess: AccessCheckResult = AccessCheckResult(
        title = "无障碍前台监听",
        status = AccessCheckStatus.Unknown,
        summary = "尚未检测",
        recommendation = "开启无障碍服务后，SmartFlight 可以通过应用切换事件实时识别前台应用。",
        isBlocking = true,
        actionType = AccessActionType.OpenSettings,
    ),
    val notificationAccess: AccessCheckResult = AccessCheckResult(
        title = "通知权限",
        status = AccessCheckStatus.Unknown,
        summary = "尚未检测",
        recommendation = "授予通知权限后，前台服务才能稳定展示运行状态。",
        isBlocking = false,
        actionType = AccessActionType.OpenSettings,
    ),
    val batteryOptimization: AccessCheckResult = AccessCheckResult(
        title = "电池优化",
        status = AccessCheckStatus.Unknown,
        summary = "尚未检测",
        recommendation = "建议允许后台不受限制运行，避免系统杀掉监听服务。",
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
