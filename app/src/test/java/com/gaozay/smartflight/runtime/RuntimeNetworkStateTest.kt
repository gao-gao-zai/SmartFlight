package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.UnifiedNetworkState
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeNetworkStateTest {
    @Test
    fun airplaneWithWifiMapsToAirplaneWithWifi() {
        assertEquals(
            UnifiedNetworkState.AirplaneWithWifi,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = true,
                isWifiConnected = true,
                isMobileDataEnabled = false,
            ),
        )
    }

    @Test
    fun airplaneWithoutWifiMapsToOffline() {
        assertEquals(
            UnifiedNetworkState.Offline,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = true,
                isWifiConnected = false,
                isMobileDataEnabled = true,
            ),
        )
    }

    @Test
    fun wifiAndCellularMapsToCombinedState() {
        assertEquals(
            UnifiedNetworkState.WifiAndCellular,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = false,
                isWifiConnected = true,
                isMobileDataEnabled = true,
            ),
        )
    }

    @Test
    fun wifiOnlyMapsToWifiOnly() {
        assertEquals(
            UnifiedNetworkState.WifiOnly,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = false,
                isWifiConnected = true,
                isMobileDataEnabled = false,
            ),
        )
    }

    @Test
    fun cellularOnlyMapsToCellularOnly() {
        assertEquals(
            UnifiedNetworkState.CellularOnly,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = false,
                isWifiConnected = false,
                isMobileDataEnabled = true,
            ),
        )
    }

    @Test
    fun unknownMobileDataWithoutWifiMapsToUnknown() {
        assertEquals(
            UnifiedNetworkState.Unknown,
            deriveUnifiedNetworkState(
                isAirplaneModeEnabled = false,
                isWifiConnected = false,
                isMobileDataEnabled = null,
            ),
        )
    }
}
