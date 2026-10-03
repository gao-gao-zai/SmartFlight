package com.gaozay.smartflight.activities

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.data.local.SmartFlightDatabase
import com.gaozay.smartflight.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityMigrationTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun versionThreeUpgradePreservesAppRulesAndExecutionHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "activity-migration-test.db"
        val app = InstalledAppEntity("fixture", "Fixture", null, false, true, true, true, false, false, true, 123)
        val log = ExecutionLogEntity(timestampMillis = 123, foregroundPackageName = "fixture", foregroundAppLabel = "Fixture",
            isWifiConnected = false, isWifiEnabled = true, isBluetoothEnabled = false, matchedRules = "original", errorMessage = null)
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, SmartFlightDatabase::class.java, name).build().let { db ->
                try { db.installedAppDao().upsert(app); db.executionLogDao().insert(log) } finally { db.close() }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                for (table in listOf("activity_components", "activity_rules", "app_activity_configs", "activity_visits")) db.execSQL("DROP TABLE $table")
                for (column in listOf("foregroundActivityName", "foregroundRuleLayer", "foregroundRuleReason")) db.execSQL("ALTER TABLE execution_logs DROP COLUMN $column")
                db.version = 3
                db.execSQL("UPDATE room_master_table SET identity_hash = 'version-three-test'")
            }
            Room.databaseBuilder(context, SmartFlightDatabase::class.java, name).addMigrations(SmartFlightDatabase.MIGRATION_3_4).build().let { db ->
                try {
                    assertEquals(app, db.installedAppDao().getByPackageName("fixture"))
                    assertEquals(log, db.executionLogDao().observeRecent(10).first().single().copy(id = 0))
                    assertTrue(db.activityDao().observeComponents("fixture").first().isEmpty())
                    assertTrue(db.activityDao().observeRuntimeRules().first().isEmpty())
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
