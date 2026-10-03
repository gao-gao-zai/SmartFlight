package com.gaozay.smartflight.data.local

import com.gaozay.smartflight.data.local.entity.*
import com.gaozay.smartflight.data.local.dao.ActivityDao
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
        ActivityComponentEntity::class,
        ActivityRuleEntity::class,
        AppActivityConfigEntity::class,
        ActivityVisitEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class SmartFlightDatabase : RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao

    abstract fun activityDao(): ActivityDao

    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS activity_components (packageName TEXT NOT NULL, className TEXT NOT NULL, targetActivity TEXT, label TEXT, isEnabled INTEGER NOT NULL, isExported INTEGER NOT NULL, isPresent INTEGER NOT NULL, isDeclared INTEGER NOT NULL, versionCode INTEGER NOT NULL, scannedAtMillis INTEGER NOT NULL, PRIMARY KEY(packageName, className))")
                db.execSQL("CREATE TABLE IF NOT EXISTS activity_rules (packageName TEXT NOT NULL, activityName TEXT NOT NULL, mode TEXT NOT NULL, note TEXT NOT NULL, PRIMARY KEY(packageName, activityName))")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_activity_configs (packageName TEXT NOT NULL, rulesEnabled INTEGER NOT NULL, lastScannedAtMillis INTEGER NOT NULL, versionCode INTEGER NOT NULL, scanError TEXT, needsReview INTEGER NOT NULL, PRIMARY KEY(packageName))")
                db.execSQL("CREATE TABLE IF NOT EXISTS activity_visits (packageName TEXT NOT NULL, activityName TEXT NOT NULL, firstEnteredAtMillis INTEGER NOT NULL, lastEnteredAtMillis INTEGER NOT NULL, source TEXT NOT NULL, sessionId TEXT, PRIMARY KEY(packageName, activityName))")
                db.execSQL("ALTER TABLE execution_logs ADD COLUMN foregroundActivityName TEXT")
                db.execSQL("ALTER TABLE execution_logs ADD COLUMN foregroundRuleLayer TEXT")
                db.execSQL("ALTER TABLE execution_logs ADD COLUMN foregroundRuleReason TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE installed_apps ADD COLUMN isInstalled INTEGER NOT NULL DEFAULT 1")
            }
        }

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
