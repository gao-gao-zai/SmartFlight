package com.gaozay.smartflight.activities

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.data.local.SmartFlightDatabase
import com.gaozay.smartflight.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityRepositoryTest {
    private val pkg = "fixture.activity"
    private fun component(name: String, target: String? = null) = ActivityComponentEntity(pkg, "$pkg.$name",
        targetActivity = target?.let { "$pkg.$it" }, isEnabled = true, isExported = false, versionCode = 1, scannedAtMillis = 10)
    private class Source(var result: ActivityPackageScan?) : ActivitySource {
        var failure = false
        override fun scanPackage(packageName: String): ActivityPackageScan? {
            check(!failure) { "fixture scan failure" }
            return result
        }
    }

    @Test
    fun aliasesShareOneRuleAndRescansPreserveNotesVisitsAndInvalidOverrides() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SmartFlightDatabase::class.java).build()
        try {
            val app = InstalledAppEntity(pkg, "Fixture", null, false, true, true, true, false, false, true, 1)
            db.installedAppDao().upsert(app)
            val source = Source(ActivityPackageScan(1, listOf(component("First"), component("Entry", "First"), component("Second"))))
            val repo = ActivityRepository(db.activityDao(), source)
            repo.refreshActivities(pkg)
            repo.saveRule(pkg, "$pkg.Entry", ActivityRuleMode.Online, " Payment ")
            repo.saveRule(pkg, "$pkg.First", ActivityRuleMode.Online, "Payment")
            repo.recordVisit(pkg, "$pkg.Entry", 100, "UsageStats", "session")
            repo.recordVisit(pkg, "$pkg.First", 200, "UsageStats", "session")
            repo.recordVisit(pkg, "$pkg.First", 150, "UsageStats", "session")
            assertEquals(1, repo.observeRuntimeRules().first().size)
            assertTrue(repo.observeRuntimeRules().first().single().isValid)
            assertEquals("Payment", repo.observeDetails(pkg).first().rules.single().note)
            assertEquals(100L, repo.observeDetails(pkg).first().visits.single().firstEnteredAtMillis)
            assertEquals(200L, repo.observeDetails(pkg).first().visits.single().lastEnteredAtMillis)
            repo.setRulesEnabled(pkg, false)
            assertFalse(repo.observeRuntimeRules().first().single().rulesEnabled)
            repo.setRulesEnabled(pkg, true)
            source.result = ActivityPackageScan(2, listOf(component("Second").copy(versionCode = 2)))
            repo.refreshActivities(pkg)
            val removed = repo.observeDetails(pkg).first()
            assertFalse(removed.components.first { it.className == "$pkg.First" }.isPresent)
            assertTrue(removed.config!!.needsReview)
            assertFalse(repo.observeRuntimeRules().first().single().isValid)
            assertEquals("Payment", removed.rules.single().note)
            // Reinstallation can restore an exact class, while keeping the review reminder.
            source.result = ActivityPackageScan(3, listOf(component("First")))
            repo.refreshActivities(pkg)
            assertTrue(repo.observeRuntimeRules().first().single().isValid)
            assertTrue(repo.observeDetails(pkg).first().config!!.needsReview)
            repo.saveRule(pkg, "$pkg.First", ActivityRuleMode.FollowApp, "Payment")
            assertTrue(repo.observeRuntimeRules().first().isEmpty())
            assertEquals("Payment", repo.observeDetails(pkg).first().rules.single().note)
            assertEquals(1, repo.observeDetails(pkg).first().visits.size)
            assertTrue(db.installedAppDao().getByPackageName(pkg)!!.isInBlacklist)
        } finally { db.close() }
    }

    @Test
    fun failedScanAndUninstallFallBackWithoutDeletingConfigurationAndRuntimeDiscoveryRequiresVerification() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SmartFlightDatabase::class.java).build()
        try {
            db.installedAppDao().upsert(InstalledAppEntity(pkg, "Fixture", null, false, true, true, true, true, true, false, 1))
            val source = Source(ActivityPackageScan(1, listOf(component("First"))))
            val repo = ActivityRepository(db.activityDao(), source)
            repo.refreshActivities(pkg)
            repo.saveRule(pkg, "$pkg.First", ActivityRuleMode.Offline, "Keep")
            source.failure = true
            try { repo.refreshActivities(pkg); fail("Expected scan failure") } catch (_: IllegalStateException) { }
            assertEquals(1, repo.observeDetails(pkg).first().components.size)
            assertEquals("Keep", repo.observeDetails(pkg).first().rules.single().note)
            assertNotNull(repo.observeDetails(pkg).first().config!!.scanError)
            assertFalse(repo.observeRuntimeRules().first().single().isValid)
            source.failure = false
            source.result = null
            repo.refreshActivities(pkg)
            assertFalse(repo.observeRuntimeRules().first().single().isValid)
            assertTrue(repo.observeDetails(pkg).first().config!!.needsReview)
            source.result = ActivityPackageScan(1, listOf(component("First")))
            repo.refreshActivities(pkg)
            repo.recordVisit(pkg, "$pkg.Dynamic", 300, "UsageStats", null)
            assertFalse(repo.observeDetails(pkg).first().components.first { it.className == "$pkg.Dynamic" }.isDeclared)
            try { repo.saveRule(pkg, "$pkg.Dynamic", ActivityRuleMode.Online, ""); fail("Expected unverified rejection") }
            catch (_: UnverifiedActivityException) { }
            assertFalse(repo.observeRuntimeRules().first().any { it.activityName == "$pkg.Dynamic" })
            source.result = ActivityPackageScan(2, listOf(component("First"), component("Dynamic")))
            repo.saveRule(pkg, "$pkg.Dynamic", ActivityRuleMode.Online, "Verified")
            assertTrue(repo.observeRuntimeRules().first().first { it.activityName == "$pkg.Dynamic" }.isValid)
        } finally { db.close() }
    }
}
