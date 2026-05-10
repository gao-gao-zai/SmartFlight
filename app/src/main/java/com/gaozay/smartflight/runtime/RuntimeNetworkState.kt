package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.UnifiedNetworkState
import com.gaozay.smartflight.settings.UserSettings

fun deriveUnifiedNetworkState(
    isAirplaneModeEnabled: Boolean?,
    isWifiConnected: Boolean,
    isMobileDataEnabled: Boolean?,
): UnifiedNetworkState {
    if (isAirplaneModeEnabled == true) {
        return if (isWifiConnected) {
            UnifiedNetworkState.AirplaneWithWifi
        } else {
            UnifiedNetworkState.Offline
        }
    }
    return when {
        isWifiConnected && isMobileDataEnabled == true -> UnifiedNetworkState.WifiAndCellular
        isWifiConnected -> UnifiedNetworkState.WifiOnly
        isMobileDataEnabled == true -> UnifiedNetworkState.CellularOnly
        isMobileDataEnabled == false -> UnifiedNetworkState.Offline
        else -> UnifiedNetworkState.Unknown
    }
}

fun RuntimeSnapshot.withDerivedUnifiedNetworkState(): RuntimeSnapshot = copy(
    unifiedNetworkState = deriveUnifiedNetworkState(
        isAirplaneModeEnabled = isAirplaneModeEnabled,
        isWifiConnected = isWifiConnected,
        isMobileDataEnabled = isMobileDataEnabled,
    ),
)

fun RuntimeSnapshot.isDisconnected(mode: NetworkControlMode): Boolean? = when (mode) {
    NetworkControlMode.AirplaneMode -> isAirplaneModeEnabled
    NetworkControlMode.MobileData -> isMobileDataEnabled?.not()
}

fun UserSettings.mobileDataNoOpSuffix(): String {
    if (networkControlMode != NetworkControlMode.MobileData) {
        return ""
    }
    val parts = buildList {
        if (preserveWifiState) add("保留 Wi‑Fi 状态：no-op")
        if (preserveBluetoothState) add("保留蓝牙状态：no-op")
    }
    return if (parts.isEmpty()) "" else "；${parts.joinToString("；")}"
}
