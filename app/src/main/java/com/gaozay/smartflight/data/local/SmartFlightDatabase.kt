package com.gaozay.smartflight.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gaozay.smartflight.data.local.dao.ExecutionLogDao
import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

@Database(
    entities = [
        InstalledAppEntity::class,
        ExecutionLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SmartFlightDatabase : RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao

    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS installed_apps_new (
                        packageName TEXT NOT NULL,
                        label TEXT NOT NULL,
                        iconCacheKey TEXT,
                        isSystemApp INTEGER NOT NULL,
                        hasLauncherEntry INTEGER NOT NULL,
                        declaresInternetPermission INTEGER NOT NULL,
                        isAutoDetectedOnline INTEGER NOT NULL,
                        isInOnlineList INTEGER NOT NULL,
                        isInWhitelist INTEGER NOT NULL,
                        isInBlacklist INTEGER NOT NULL,
                        lastScannedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(packageName)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO installed_apps_new (
                        packageName,
                        label,
                        iconCacheKey,
                        isSystemApp,
                        hasLauncherEntry,
                        declaresInternetPermission,
                        isAutoDetectedOnline,
                        isInOnlineList,
                        isInWhitelist,
                        isInBlacklist,
                        lastScannedAtMillis
                    )
                    SELECT
                        packageName,
                        label,
                        iconCacheKey,
                        isSystemApp,
                        hasLauncherEntry,
                        declaresInternetPermission,
                        CASE WHEN listStatus = 'Candidate' THEN 1 ELSE 0 END,
                        CASE WHEN listStatus IN ('Candidate', 'Whitelist') THEN 1 ELSE 0 END,
                        CASE WHEN listStatus = 'Whitelist' THEN 1 ELSE 0 END,
                        CASE WHEN listStatus = 'Blacklist' THEN 1 ELSE 0 END,
                        lastScannedAtMillis
                    FROM installed_apps
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE installed_apps")
                db.execSQL("ALTER TABLE installed_apps_new RENAME TO installed_apps")
            }
        }
    }
}
