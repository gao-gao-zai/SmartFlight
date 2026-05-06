package com.gaozay.smartflight.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val triggerSource: String = TriggerSource.Manual.name,
    val foregroundPackageName: String?,
    val foregroundAppLabel: String?,
    val screenState: String = ScreenState.Unknown.name,
    val isWifiConnected: Boolean,
    val isWifiEnabled: Boolean,
    val isBluetoothEnabled: Boolean,
    val matchedRules: String,
    val actionType: String = ExecutionAction.DoNothing.name,
    val executorType: String = ExecutorType.Unavailable.name,
    val result: String = ExecutionResult.Pending.name,
    val errorMessage: String?,
)
