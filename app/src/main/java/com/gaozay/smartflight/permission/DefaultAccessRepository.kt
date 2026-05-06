package com.gaozay.smartflight.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAccessRepository @Inject constructor(
    private val advancedAccessChecker: AdvancedAccessChecker,
    private val systemPermissionChecker: SystemPermissionChecker,
) : AccessRepository {
    private val mutableAccessGateState = MutableStateFlow(AccessGateState())

    override val accessGateState: StateFlow<AccessGateState> = mutableAccessGateState.asStateFlow()

    override suspend fun refresh() {
        mutableAccessGateState.value = AccessGateState(
            advancedAccess = advancedAccessChecker.check(),
            usageStatsAccess = systemPermissionChecker.checkUsageStatsAccess(),
            notificationAccess = systemPermissionChecker.checkNotificationPermission(),
            batteryOptimization = systemPermissionChecker.checkBatteryOptimization(),
            lastCheckedAtMillis = System.currentTimeMillis(),
        )
    }
}
