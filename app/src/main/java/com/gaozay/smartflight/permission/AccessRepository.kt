package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.TriggerSource
import kotlinx.coroutines.flow.StateFlow

interface AccessRepository {
    val accessGateState: StateFlow<AccessGateState>

    suspend fun refresh()

    suspend fun setAdbBootstrapped(bootstrapped: Boolean)

    suspend fun probeRootAccess()

    suspend fun autoGrantCompanionPermissions()

    suspend fun syncCurrentNetworkControlState()

    suspend fun probeCurrentNetworkControlState()

    suspend fun toggleCurrentNetworkControlState()

    suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: TriggerSource = TriggerSource.Manual,
        reason: String? = null,
    )
}
