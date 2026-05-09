package com.gaozay.smartflight.settings

import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette

data class UserSettings(
    val automationEnabled: Boolean = false,
    val networkControlMode: NetworkControlMode = NetworkControlMode.AirplaneMode,
    val preferredExecutorType: ExecutorType = ExecutorType.Auto,
    val screenOffDisconnectEnabled: Boolean = true,
    val screenOffDelaySeconds: Int = 60,
    val appExitDisconnectEnabled: Boolean = true,
    val appExitDelaySeconds: Int = 30,
    val reconnectOnTargetAppLaunch: Boolean = true,
    val monitorForegroundWhenScreenOff: Boolean = false,
    val whitelistOnly: Boolean = false,
    val skipReconnectOnWifi: Boolean = true,
    val skipDisconnectOnWifi: Boolean = true,
    val preserveWifiState: Boolean = true,
    val preserveBluetoothState: Boolean = true,
    val disableScreenOnReconnect: Boolean = true,
    val disableUnlockReconnect: Boolean = true,
    val showReconnectPrompt: Boolean = true,
    val reconnectPromptText: String = "SmartFlight 已恢复联网",
    val showDisconnectPrompt: Boolean = true,
    val disconnectPromptText: String = "SmartFlight 已断网",
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.LogoOriginal,
    val customSeedColorArgb: Int = ThemePalette.LogoOriginal.seedColorArgb,
    val themeIntensity: ThemeIntensity = ThemeIntensity.Standard,
    val cornerStyle: CornerStyle = CornerStyle.Standard,
)
