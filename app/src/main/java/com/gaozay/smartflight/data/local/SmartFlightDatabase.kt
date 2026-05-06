package com.gaozay.smartflight.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gaozay.smartflight.data.local.dao.ExecutionLogDao
import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

@Database(
    entities = [
        InstalledAppEntity::class,
        ExecutionLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SmartFlightDatabase : RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao

    abstract fun executionLogDao(): ExecutionLogDao
}
