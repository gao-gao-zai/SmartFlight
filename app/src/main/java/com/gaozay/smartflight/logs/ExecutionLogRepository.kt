package com.gaozay.smartflight.logs

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import kotlinx.coroutines.flow.Flow

interface ExecutionLogRepository {
    fun observeRecentLogs(limit: Int = 100): Flow<List<ExecutionLogEntity>>

    fun observeLogCount(): Flow<Int>

    suspend fun addLog(log: ExecutionLogEntity): Long

    suspend fun clearLogs()
}
