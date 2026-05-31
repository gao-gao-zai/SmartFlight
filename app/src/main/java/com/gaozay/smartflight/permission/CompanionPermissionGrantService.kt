package com.gaozay.smartflight.permission

import android.content.Context
import android.os.Build
import com.gaozay.smartflight.executor.ExecutorWriteCommands
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CompanionPermissionGrantService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkControlProbe: NetworkControlProbe,
) {
    suspend fun autoGrantCompanionPermissions(currentState: AccessGateState) {
        if (!currentState.advancedAccess.isAvailable) {
            return
        }

        val packageName = context.packageName
        if (!currentState.usageStatsAccess.satisfiesRequirement) {
            networkControlProbe.runCommand(
                ExecutorWriteCommands.grantUsageStatsAccess(packageName),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !currentState.notificationAccess.satisfiesRequirement
        ) {
            networkControlProbe.runCommand(
                ExecutorWriteCommands.grantNotificationPermission(packageName),
            )
        }
        if (!currentState.batteryOptimization.satisfiesRequirement) {
            networkControlProbe.runCommand(
                ExecutorWriteCommands.whitelistBatteryOptimization(packageName),
            )
        }
    }
}
