package com.gaozay.smartflight.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionLogEntity>>

    @Insert
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM execution_logs")
    fun observeCount(): Flow<Int>
}
