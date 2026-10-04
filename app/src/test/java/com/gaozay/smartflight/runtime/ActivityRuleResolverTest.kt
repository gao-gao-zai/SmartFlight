package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.activities.ActivityRuntimeRule
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import org.junit.Assert.*
import org.junit.Test

class ActivityRuleResolverTest {
    private val pkg = "example.app"
    private val online = AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Auto)
    private val offline = AppRuntimeRuleInfo(false, true, AppOnlineSourceTag.Manual)
    private fun info(name: String? = "$pkg.Payment", confirmed: Boolean = true, verified: Long = 1000) =
        ForegroundAppInfo(pkg, "Example", 1000, name, ForegroundInfoSource.UsageStats, confirmed, verified)
    private fun rule(mode: String = "Online", enabled: Boolean = true, valid: Boolean = true, name: String = "$pkg.Payment") =
        ActivityRuntimeRule(pkg, name, mode, enabled, valid)

    @Test fun onlineOverrideCanOverrideAnAppBlacklist() {
        val result = resolveActivityRule(info(), offline, listOf(rule()), 1000)
        val resolved = requireNotNull(result.rule)
        assertTrue(resolved.isInOnlineList)
        assertFalse(resolved.isInBlacklist)
        assertEquals(AppOnlineSourceTag.Manual, resolved.sourceTag)
        assertEquals(ActivityMatchReason.ActivityOverride, result.reason)
    }
    @Test fun offlineOverrideKeepsManualOfflineSemantics() {
        val result = resolveActivityRule(info(), online, listOf(rule("Offline")), 1000)
        val resolved = requireNotNull(result.rule)
        assertFalse(resolved.isInOnlineList)
        assertTrue(resolved.isInBlacklist)
    }
    @Test fun noOverrideDoesNotChangeAnyAppFlags() {
        for (app in listOf(online, offline, AppRuntimeRuleInfo(false, false, null))) {
            assertEquals(app, resolveActivityRule(info(), app, emptyList(), 1000).rule)
            assertEquals(app, resolveActivityRule(info("$pkg.Other"), app, listOf(rule()), 1000).rule)
        }
    }
    @Test fun exactMatchDoesNotMatchSimilarNamesOrOtherPackages() {
        assertEquals(offline, resolveActivityRule(info("$pkg.PaymentExtra"), offline, listOf(rule()), 1000).rule)
        assertEquals(offline, resolveActivityRule(info().copy(packageName = "other.app"), offline, listOf(rule()), 1000).rule)
    }
    @Test fun unknownAndUntrustedClassesUseTheAppDefault() {
        for (app in listOf(info(null), info(confirmed = false))) {
            val result = resolveActivityRule(app, offline, listOf(rule()), 1000)
            assertEquals(offline, result.rule)
            assertEquals(ActivityMatchReason.Unknown, result.reason)
        }
    }
    @Test fun staleAndPausedOrInvalidRulesReportTheirFallbackReasons() {
        assertEquals(ActivityMatchReason.Stale, resolveActivityRule(info(verified = 1), offline, listOf(rule()), 31_002).reason)
        assertEquals(ActivityMatchReason.Paused, resolveActivityRule(info(), offline, listOf(rule(enabled = false)), 1000).reason)
        assertEquals(ActivityMatchReason.Invalid, resolveActivityRule(info(), offline, listOf(rule(valid = false)), 1000).reason)
    }
    @Test fun followAppRemovesOnlyTheOverride() {
        assertEquals(offline, resolveActivityRule(info(), offline, listOf(rule("FollowApp")), 1000).rule)
        assertEquals(ActivityMatchReason.AppDefault, resolveActivityRule(info(), offline, listOf(rule("FollowApp")), 1000).reason)
    }
    @Test fun oldEventsCannotReplaceANewerConfirmedActivityOrPackage() {
        val latest = info().copy(eventTimestampMillis = 2000, activityName = "$pkg.New")
        assertEquals(latest, chooseForegroundUpdate(latest, info()))
        assertEquals(latest, chooseForegroundUpdate(latest, info().copy(packageName = "other.app", source = ForegroundInfoSource.Unknown)))
    }
    @Test fun changingSourceRetainsPackageButRequiresNewActivityEvidence() {
        val original = info()
        val cleared = original.withoutActivityConfirmation()
        assertEquals(original.packageName, cleared.packageName)
        assertEquals(original.eventTimestampMillis, cleared.eventTimestampMillis)
        assertNull(cleared.confirmedActivity(1000))
        assertEquals(offline, resolveActivityRule(cleared, offline, listOf(rule()), 1000).rule)
    }
    @Test fun permissionLossClearsConfidenceWithoutRestoringAnOlderActivity() {
        val latest = info().copy(eventTimestampMillis = 2000)
        val lost = chooseForegroundUpdate(latest, info().copy(source = ForegroundInfoSource.Unknown, activityConfirmed = false))!!
        assertNull(lost.activityName)
        assertFalse(lost.activityConfirmed)
        assertEquals(2000L, lost.eventTimestampMillis)
    }
}
