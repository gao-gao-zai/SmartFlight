package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationRuntimeCoordinatorTest {
    @Test
    fun screenOnProbeIntervalKeepsOriginalCadence() {
        assertEquals(
            1_500L,
            AutomationRuntimeCoordinator.foregroundProbeIntervalMillis(ScreenState.ScreenOn),
        )
        assertEquals(
            1_500L,
            AutomationRuntimeCoordinator.foregroundProbeIntervalMillis(ScreenState.Unlocked),
        )
    }

    @Test
    fun screenOffMonitoringUsesActiveScreenOffCadenceOnlyWhenEnabledByRule() {
        assertEquals(
            8_000L,
            AutomationRuntimeCoordinator.foregroundProbeIntervalMillis(ScreenState.ScreenOff),
        )
    }
}
