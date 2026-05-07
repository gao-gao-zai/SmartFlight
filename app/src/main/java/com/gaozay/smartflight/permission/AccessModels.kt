package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutorType

data class AccessCheckResult(
    val title: String,
    val available: Boolean,
    val summary: String,
    val recommendation: String,
)

data class AdvancedAccessState(
    val selectedExecutorType: ExecutorType = ExecutorType.Unavailable,
    val checks: List<AccessCheckResult> = emptyList(),
) {
    val isAvailable: Boolean = checks.any { it.available }
}

data class AccessGateState(
    val advancedAccess: AdvancedAccessState = AdvancedAccessState(),
    val usageStatsAccess: AccessCheckResult = AccessCheckResult(
        title = "使用情况访问权限",
        available = false,
        summary = "尚未检测",
        recommendation = "授予使用情况访问权限后，SmartFlight 才能判断当前前台应用。",
    ),
    val notificationAccess: AccessCheckResult = AccessCheckResult(
        title = "通知权限",
        available = false,
        summary = "尚未检测",
        recommendation = "授予通知权限后，前台服务才能稳定展示运行状态。",
    ),
    val batteryOptimization: AccessCheckResult = AccessCheckResult(
        title = "电池优化",
        available = false,
        summary = "尚未检测",
        recommendation = "建议允许后台不受限制运行，避免系统杀掉监听服务。",
    ),
    val lastCheckedAtMillis: Long = 0,
) {
    val canEnterApp: Boolean = advancedAccess.isAvailable && usageStatsAccess.available
}
