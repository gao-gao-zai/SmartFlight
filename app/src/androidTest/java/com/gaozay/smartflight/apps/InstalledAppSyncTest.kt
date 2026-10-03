package com.gaozay.smartflight.apps

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.data.local.SmartFlightDatabase
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledAppSyncTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixture = "com.gaozay.smartflight.syncfixture"

    @Test
    fun startupAndRealInstallUpdateUninstallReinstallSynchronizeWithoutManualScan() = runBlocking {
        val repository = EntryPointAccessors.fromApplication(context, AppSyncTestEntryPoint::class.java)
            .installedAppRepository()
        // CI installs the fixture before starting this process; no broadcast could reach our listener.
        awaitApp(repository) { it?.isAutoDetectedOnline == true }
        shell("pm uninstall $fixture")
        awaitApp(repository) { it == null }
        try {
            install(1)
            awaitApp(repository) { it?.isAutoDetectedOnline == true }
            repository.setManualOffline(fixture)
            install(2)
            val updated = awaitApp(repository) { it?.label == "Package Sync Fixture 2" }
            assertFalse(updated!!.declaresInternetPermission)
            assertFalse(updated.isAutoDetectedOnline)
            assertTrue(updated.isInBlacklist)
            assertFalse(updated.isInOnlineList)
            shell("pm uninstall $fixture").also { assertTrue(it, it.contains("Success")) }
            awaitApp(repository) { it == null }
            assertFalse(repository.observeApps().first().any { it.packageName == fixture })
            install(1)
            val restored = awaitApp(repository) { it?.isAutoDetectedOnline == true }
            assertTrue(restored!!.isInBlacklist)
            assertFalse(restored.isInOnlineList)
        } finally {
            shell("pm uninstall $fixture")
            repository.refreshPackage(fixture, removeIfMissing = true)
        }
    }

    @Test
    fun protectedEventDecodingIgnoresUpgradeRemovalAndInvalidData() {
        val uri = Uri.parse("package:$fixture")
        assertNull(Intent(Intent.ACTION_PACKAGE_REMOVED, uri)
            .putExtra(Intent.EXTRA_REPLACING, true).toPackageChange())
        assertEquals(PackageChange.Refresh(fixture, true), Intent(Intent.ACTION_PACKAGE_REMOVED, uri).toPackageChange())
        for (action in listOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_CHANGED)) {
            assertEquals(PackageChange.Refresh(fixture), Intent(action, uri).toPackageChange())
        }
        assertNull(Intent(Intent.ACTION_PACKAGE_ADDED).toPackageChange())
        assertNull(Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("https://example.com")).toPackageChange())
        assertNull(Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:")).toPackageChange())
        assertNull(Intent(Intent.ACTION_VIEW, uri).toPackageChange())
    }

    @Test
    fun roomArchiveFiltersActiveFlowsAndEmptyReconciliationKeepsSavedRules() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, SmartFlightDatabase::class.java).build()
        try {
            val dao = database.installedAppDao()
            val app = InstalledAppEntity(fixture, "Fixture", null, false, true, true, true, false, false, true, 1)
            dao.upsert(app)
            dao.replaceScannedApps(emptyList())
            assertTrue(dao.observeAll().first().isEmpty())
            assertEquals(0, dao.observeCount().first())
            assertTrue(dao.getByPackageName(fixture)!!.isInBlacklist)
            assertFalse(dao.getByPackageName(fixture)!!.isInstalled)
            dao.upsert(app)
            assertEquals(listOf(app), dao.observeAll().first())
            assertEquals(1, dao.observeCount().first())
        } finally {
            database.close()
        }
    }

    private fun install(version: Int) {
        val output = shell("pm install -r /data/local/tmp/smartflight-fixture-v$version.apk")
        assertTrue("Fixture APK must be prepared by scripts/run_emulator_tests.sh: $output", output.contains("Success"))
    }

    private suspend fun awaitApp(repository: InstalledAppRepository, predicate: (InstalledAppEntity?) -> Boolean): InstalledAppEntity? {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var app: InstalledAppEntity?
        do {
            app = repository.getApp(fixture)
            if (predicate(app)) return app
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for automatic package sync; last state: $app")
        return null
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
}
