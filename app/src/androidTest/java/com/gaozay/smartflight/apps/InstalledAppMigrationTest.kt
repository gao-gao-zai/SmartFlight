package com.gaozay.smartflight.apps

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.data.local.SmartFlightDatabase
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledAppMigrationTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun versionTwoUpgradePreservesManualRulesAndMakesExistingRowsActive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "package-sync-migration-test.db"
        context.deleteDatabase(name)
        val original = InstalledAppEntity("fixture", "Fixture", null, false, true, true, true, false, false, true, 123)
        try {
            // Preserve the exact unrelated tables/indices, then recreate the pre-change app schema.
            Room.databaseBuilder(context, SmartFlightDatabase::class.java, name).build().let { db ->
                try { db.installedAppDao().upsert(original) } finally { db.close() }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("ALTER TABLE installed_apps DROP COLUMN isInstalled")
                db.version = 2
                db.execSQL("UPDATE room_master_table SET identity_hash = 'version-two-test'")
            }
            Room.databaseBuilder(context, SmartFlightDatabase::class.java, name)
                .addMigrations(SmartFlightDatabase.MIGRATION_1_2, SmartFlightDatabase.MIGRATION_2_3)
                .build().let { db ->
                    try {
                        assertEquals(original, db.installedAppDao().getByPackageName("fixture"))
                        assertEquals(listOf(original), db.installedAppDao().observeAll().first())
                        assertEquals(1, db.installedAppDao().observeCount().first())
                    } finally { db.close() }
                }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
