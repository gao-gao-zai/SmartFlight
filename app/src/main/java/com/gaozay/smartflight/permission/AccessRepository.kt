package com.gaozay.smartflight.permission

import kotlinx.coroutines.flow.StateFlow

interface AccessRepository {
    val accessGateState: StateFlow<AccessGateState>

    suspend fun refresh()
}
