package com.gaozay.smartflight

import com.gaozay.smartflight.permission.AccessRepository
import javax.inject.Inject

class AccessActions @Inject constructor(
    private val accessRepository: AccessRepository,
) {
    suspend fun refreshAccessChecks() {
        accessRepository.refresh()
    }

    suspend fun setAdbBootstrapped(bootstrapped: Boolean) {
        accessRepository.setAdbBootstrapped(bootstrapped)
    }

    suspend fun probeRootAccess() {
        accessRepository.probeRootAccess()
    }

    suspend fun autoGrantCompanionPermissions() {
        accessRepository.autoGrantCompanionPermissions()
    }

    suspend fun syncCurrentNetworkControlState() {
        accessRepository.syncCurrentNetworkControlState()
    }

    suspend fun probeCurrentNetworkControlState() {
        accessRepository.probeCurrentNetworkControlState()
    }

    suspend fun toggleCurrentNetworkControlState() {
        accessRepository.toggleCurrentNetworkControlState()
    }
}
