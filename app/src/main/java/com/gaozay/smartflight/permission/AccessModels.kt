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
        title = "Usage access",
        available = false,
        summary = "Not checked yet",
        recommendation = "Grant usage access to allow foreground app detection.",
    ),
    val notificationAccess: AccessCheckResult = AccessCheckResult(
        title = "Notification permission",
        available = false,
        summary = "Not checked yet",
        recommendation = "Grant notification permission for foreground service status.",
    ),
    val batteryOptimization: AccessCheckResult = AccessCheckResult(
        title = "Battery optimization",
        available = false,
        summary = "Not checked yet",
        recommendation = "Allow unrestricted background execution where possible.",
    ),
    val lastCheckedAtMillis: Long = 0,
) {
    val canEnterApp: Boolean = advancedAccess.isAvailable && usageStatsAccess.available
}
