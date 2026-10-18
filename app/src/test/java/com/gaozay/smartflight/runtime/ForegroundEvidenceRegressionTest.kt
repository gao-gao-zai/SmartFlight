package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.i18n.LocalizedStringsTest
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.quickrule.QuickRuleContract
import com.gaozay.smartflight.quickrule.QuickRuleOverlayState
import org.junit.Assert.*
import org.junit.Test

class ForegroundEvidenceRegressionTest : LocalizedStringsTest() {
    private class Evidence {
        var now = 100_000L
        var allowed = true
        var failed = false
        var events: List<ForegroundUsageEvent>? = emptyList()
        val observations = ForegroundObservationStore()
        val declared = setOf("online.Main", "online.Second", "offline.Main", "launcher.Home")
        val tracker = AccessibilityForegroundAppTracker("self", { _, name -> name?.takeIf { it in declared } }, observations, { it }, { now })
        val usage = ForegroundAppDetector("self", { _, name -> name?.takeIf { it in declared } }, observations,
            { allowed }, { _, _ -> if (failed) throw SecurityException() else events?.asSequence() }, { it }, { now })
        val hybrid = HybridForegroundAppSource(tracker, usage, observations)
        fun resume(pkg: String, activity: String, at: Long = now) { events = listOf(ForegroundUsageEvent(pkg, activity, at, 1)) }
    }

    @Test fun quietOnlineAppSurvivesLongStayAndForeignDialogImeSystemUiCandidates() {
        val f = Evidence()
        f.tracker.markServiceConnected()
        f.tracker.recordPackage("online", f.now, "online.Main")
        f.resume("online", "online.Main")
        assertEquals("online", f.hybrid.detect()!!.packageName)
        f.events = emptyList()
        for ((pkg, window) in listOf("online" to "android.app.Dialog", "ime" to "InputMethod",
                "com.android.systemui" to "android.widget.FrameLayout", "foreign" to "Dialog")) {
            f.now += 48 * 60 * 60 * 1000L
            assertNull(f.tracker.recordPackage(pkg, f.now, window))
            val app = f.hybrid.detect()!!
            assertEquals("online", app.packageName)
            assertEquals("online.Main", app.confirmedActivity(f.now))
        }
        f.tracker.recordPackage("launcher", ++f.now, "launcher.Home")
        assertEquals("launcher", f.hybrid.detect()!!.packageName)
    }

    @Test fun usagePauseStopDoNotInvalidateNewerActivityButInvalidateMatchingResume() {
        val f = Evidence()
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", 100, 1),
            ForegroundUsageEvent("online", "online.Second", 101, 1),
            ForegroundUsageEvent("online", "online.Main", 102, 2))
        assertEquals("online.Second", f.usage.detect()!!.activityName)
        f.events = listOf(ForegroundUsageEvent("online", "online.Second", 103, 23))
        assertNull(f.usage.detect())
        assertEquals(103L, f.usage.latestLifecycleTimestampMillis)
        f.events = listOf(ForegroundUsageEvent("offline", "offline.Main", 104, 1))
        assertEquals("offline", f.usage.detect()!!.packageName)
    }

    @Test fun nullQueryPermissionRevocationAndSecurityExceptionRemainUnknownUntilRecovery() {
        val f = Evidence()
        f.resume("online", "online.Main")
        assertNotNull(f.usage.detect())
        f.events = null
        assertNull(f.usage.detect())
        f.allowed = false
        assertNull(f.usage.detect())
        f.allowed = true
        f.events = emptyList()
        assertNull(f.usage.detect())
        f.resume("offline", "offline.Main", ++f.now)
        assertEquals("offline", f.usage.detect()!!.packageName)
        f.failed = true
        assertNull(f.usage.detect())
        f.failed = false
        f.events = emptyList()
        assertNull(f.usage.detect())
    }

    @Test fun sourceModesDoNotBorrowAccessibilityAndAutoRecoversFromDisconnect() {
        val f = Evidence()
        f.tracker.markServiceConnected()
        f.tracker.recordPackage("online", f.now, "online.Main")
        f.resume("offline", "offline.Main", f.now - 1)
        assertEquals("online", f.hybrid.detect()!!.packageName)
        f.hybrid.monitorMode = ForegroundMonitorMode.UsageStats
        assertEquals("offline", f.hybrid.detect()!!.packageName)
        f.hybrid.monitorMode = ForegroundMonitorMode.Accessibility
        assertEquals("online", f.hybrid.detect()!!.packageName)
        f.tracker.markServiceDisconnected()
        assertNull(f.hybrid.detect())
        f.hybrid.monitorMode = ForegroundMonitorMode.Auto
        assertEquals("offline", f.hybrid.detect()!!.packageName)
        f.allowed = false
        assertNull(f.hybrid.detect())
        f.tracker.markServiceConnected()
        f.tracker.recordPackage("launcher", ++f.now, "launcher.Home")
        assertEquals("launcher", f.hybrid.detect()!!.packageName)
    }

    @Test fun newerUsagePauseInvalidatesOldAccessibilityAndResumeRestoresEvidence() {
        val f = Evidence()
        f.tracker.markServiceConnected()
        f.tracker.recordPackage("online", f.now, "online.Main")
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", f.now, 1),
            ForegroundUsageEvent("online", "online.Main", f.now + 1, 2))
        assertNull(f.hybrid.detect())
        f.resume("offline", "offline.Main", f.now + 2)
        assertEquals("offline", f.hybrid.detect()!!.packageName)
    }

    @Test fun quickEditorRestoresOnlyImmediatelyPausedUnderlyingActivityAcrossQueries() {
        val f = Evidence()
        f.resume("online", "online.Main", 100)
        assertNotNull(f.usage.detect())
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", 101, 2))
        assertNull(f.usage.detect())
        f.events = listOf(ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 102, 1),
            ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 103, 2),
            ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 104, 23))
        assertEquals("online.Main", f.usage.detect()!!.activityName)
        f.events = emptyList()
        assertEquals("online", f.usage.detect()!!.packageName)
        f.resume("offline", "offline.Main", 105)
        assertEquals("offline", f.usage.detect()!!.packageName)
    }

    @Test fun quickEditorCannotReviveStoppedOrPermissionInvalidatedEvidence() {
        val f = Evidence()
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", 100, 1),
            ForegroundUsageEvent("online", "online.Main", 101, 2),
            ForegroundUsageEvent("online", "online.Main", 102, 23),
            ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 103, 1))
        assertNull(f.usage.detect())
        f.resume("online", "online.Main", 104)
        assertNotNull(f.usage.detect())
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", 105, 2))
        assertNull(f.usage.detect())
        f.allowed = false
        assertNull(f.usage.detect())
        f.allowed = true
        f.events = listOf(ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 106, 1))
        assertNull(f.usage.detect())
    }

    @Test fun quickEditorDoesNotHideRealHostActivityOrUnderlyingStop() {
        val f = Evidence()
        f.events = listOf(ForegroundUsageEvent("online", "online.Main", 100, 1),
            ForegroundUsageEvent("online", "online.Main", 101, 2),
            ForegroundUsageEvent("self", QuickRuleContract.ACTIVITY_CLASS, 102, 1),
            ForegroundUsageEvent("online", "online.Main", 103, 23))
        assertNull(f.usage.detect())
        QuickRuleOverlayState.entered()
        try {
            f.resume("self", "self.Main", 104)
            assertEquals("self.Main", f.usage.detect()!!.activityName)
        } finally { QuickRuleOverlayState.left() }
    }

    @Test fun duplicateQueriesAndClockRollbackDoNotRevivePreviousEpoch() {
        val f = Evidence()
        f.resume("online", "online.Main")
        assertNotNull(f.usage.detect())
        f.usage.invalidateActivityConfirmation()
        assertFalse(f.usage.detect()!!.activityConfirmed)
        f.now = 90_000L
        f.events = emptyList()
        assertNull(f.usage.detect())
        f.tracker.markServiceConnected()
        f.tracker.recordPackage("online", f.now, "online.Main")
        assertNull(f.tracker.recordPackage("offline", f.now - 1, "offline.Main"))
        f.now -= 1000
        assertNull(f.tracker.latest())
    }
}
