package com.gaozay.smartflight.permission

import kotlinx.coroutines.flow.StateFlow

interface AccessRepository {
    val accessGateState: StateFlow<AccessGateState>

    suspend fun refresh()

    suspend fun setAdbBootstrapped(bootstrapped: Boolean)

    suspend fun probeRootAccess()

    suspend fun probeAirplaneModeState()
}
