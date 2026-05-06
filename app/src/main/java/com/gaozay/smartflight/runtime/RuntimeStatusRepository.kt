package com.gaozay.smartflight.runtime

import kotlinx.coroutines.flow.Flow

interface RuntimeStatusRepository {
    val snapshot: Flow<RuntimeSnapshot>

    suspend fun updateSnapshot(transform: (RuntimeSnapshot) -> RuntimeSnapshot)
}
