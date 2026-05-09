package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.domain.model.UnifiedNetworkState

data class RuntimeSnapshot(
    val currentForegroundPackageName: String? = null,
    val currentForegroundAppLabel: String? = null,
    val screenState: ScreenState = ScreenState.Unknown,
    val unifiedNetworkState: UnifiedNetworkState = UnifiedNetworkState.Unknown,
    val isAirplaneModeEnabled: Boolean? = null,
    val isWifiConnected: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isForegroundServiceRunning: Boolean = false,
    val isScreenOffDisconnectScheduled: Boolean = false,
    val pendingScreenOffDisconnectAtMillis: Long? = null,
    val isAppExitDisconnectScheduled: Boolean = false,
    val lastAction: ExecutionAction = ExecutionAction.DoNothing,
    val lastTriggerSource: TriggerSource = TriggerSource.ServiceRestored,
    val lastActionResult: ExecutionResult = ExecutionResult.Pending,
    val lastActionReason: String = "Runtime not started yet",
    val activeExecutorType: ExecutorType = ExecutorType.Unavailable,
    val updatedAtMillis: Long = 0,
)
