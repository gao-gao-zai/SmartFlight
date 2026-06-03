package com.gaozay.smartflight.permission

import android.util.Log
import javax.inject.Inject

class AccessGateRefresher @Inject constructor(
    private val advancedAccessChecker: AdvancedAccessChecker,
    private val systemPermissionChecker: SystemPermissionChecker,
    private val snapshotUpdater: AccessRuntimeSnapshotUpdater,
) {
    suspend fun refresh(): AccessGateState {
        val advancedAccess = advancedAccessChecker.check()
        val state = AccessGateState(
            advancedAccess = advancedAccess,
            usageStatsAccess = systemPermissionChecker.checkUsageStatsAccess(),
            accessibilityAccess = systemPermissionChecker.checkAccessibilityAccess(),
            notificationAccess = systemPermissionChecker.checkNotificationPermission(),
            batteryOptimization = systemPermissionChecker.checkBatteryOptimization(),
            lastCheckedAtMillis = System.currentTimeMillis(),
        )
        Log.d(
            LOG_TAG,
            "refresh executor=${advancedAccess.selectedExecutorType} available=${advancedAccess.isAvailable} issues=${advancedAccess.gatingIssues.joinToString { it.summary }}",
        )
        snapshotUpdater.updatePassiveAccessSummary(advancedAccess)
        return state
    }

    private companion object {
        const val LOG_TAG = "SmartFlightAccess"
    }
}
