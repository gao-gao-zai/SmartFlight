package com.gaozay.smartflight.logs

import com.gaozay.smartflight.data.local.dao.ExecutionLogDao
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExecutionLogRepository @Inject constructor(
    private val executionLogDao: ExecutionLogDao,
) : ExecutionLogRepository {
    override fun observeRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>> =
        executionLogDao.observeRecent(limit)

    override fun observeLogCount(): Flow<Int> = executionLogDao.observeCount()

    override suspend fun addLog(log: ExecutionLogEntity): Long = executionLogDao.insert(log)

    override suspend fun clearLogs() {
        executionLogDao.clear()
    }
}
