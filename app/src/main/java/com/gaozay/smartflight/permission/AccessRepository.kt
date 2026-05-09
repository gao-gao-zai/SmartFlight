package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.TriggerSource
import kotlinx.coroutines.flow.StateFlow

interface AccessRepository {
    val accessGateState: StateFlow<AccessGateState>

    suspend fun refresh()

    suspend fun setAdbBootstrapped(bootstrapped: Boolean)

    suspend fun probeRootAccess()

    suspend fun probeAirplaneModeState()

    suspend fun toggleAirplaneModeState()

    suspend fun setAirplaneModeState(
        enabled: Boolean,
        triggerSource: TriggerSource = TriggerSource.Manual,
        reason: String? = null,
    )
}
