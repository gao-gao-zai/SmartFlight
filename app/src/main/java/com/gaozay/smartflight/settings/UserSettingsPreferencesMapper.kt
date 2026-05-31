package com.gaozay.smartflight.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette

fun Preferences.toUserSettings(): UserSettings = UserSettings(
    automationEnabled = this[SettingsPreferenceKeys.AutomationEnabled] ?: false,
    pauseAutomationOnExternalNetworkChange = this[SettingsPreferenceKeys.PauseAutomationOnExternalNetworkChange] ?: true,
    temporaryDisableMode = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.TemporaryDisableMode],
        default = AutomationDisableMode.None,
    ),
    temporaryDisableStartedAtMillis = this[SettingsPreferenceKeys.TemporaryDisableStartedAtMillis] ?: 0L,
    temporaryDisableUntilMillis = this[SettingsPreferenceKeys.TemporaryDisableUntilMillis],
    temporaryDisableForegroundPackageName = this[SettingsPreferenceKeys.TemporaryDisableForegroundPackageName],
    networkControlMode = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.NetworkControlMode],
        default = NetworkControlMode.AirplaneMode,
    ),
    preferredExecutorType = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.PreferredExecutorType],
        default = ExecutorType.Auto,
    ),
    screenOffDisconnectEnabled = this[SettingsPreferenceKeys.ScreenOffDisconnectEnabled] ?: true,
    screenOffDelaySeconds = this[SettingsPreferenceKeys.ScreenOffDelaySeconds] ?: 60,
    appExitDisconnectEnabled = this[SettingsPreferenceKeys.AppExitDisconnectEnabled] ?: true,
    appExitDelaySeconds = this[SettingsPreferenceKeys.AppExitDelaySeconds] ?: 30,
    reconnectOnTargetAppLaunch = this[SettingsPreferenceKeys.ReconnectOnTargetAppLaunch] ?: true,
    monitorForegroundWhenScreenOff = this[SettingsPreferenceKeys.MonitorForegroundWhenScreenOff] ?: false,
    skipReconnectOnWifi = this[SettingsPreferenceKeys.SkipReconnectOnWifi] ?: true,
    skipDisconnectOnWifi = this[SettingsPreferenceKeys.SkipDisconnectOnWifi] ?: true,
    preserveWifiState = this[SettingsPreferenceKeys.PreserveWifiState] ?: true,
    preserveBluetoothState = this[SettingsPreferenceKeys.PreserveBluetoothState] ?: true,
    disableScreenOnReconnect = this[SettingsPreferenceKeys.DisableScreenOnReconnect] ?: true,
    disableUnlockReconnect = this[SettingsPreferenceKeys.DisableUnlockReconnect] ?: true,
    showReconnectPrompt = this[SettingsPreferenceKeys.ShowReconnectPrompt] ?: true,
    reconnectPromptText = this[SettingsPreferenceKeys.ReconnectPromptText] ?: "SmartFlight 已恢复联网",
    showDisconnectPrompt = this[SettingsPreferenceKeys.ShowDisconnectPrompt] ?: true,
    disconnectPromptText = this[SettingsPreferenceKeys.DisconnectPromptText] ?: "SmartFlight 已断网",
    themeMode = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.ThemeMode],
        default = ThemeMode.System,
    ),
    themePalette = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.ThemePalette],
        default = ThemePalette.LogoOriginal,
    ),
    customSeedColorArgb = this[SettingsPreferenceKeys.CustomSeedColorArgb] ?: ThemePalette.LogoOriginal.seedColorArgb,
    themeIntensity = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.ThemeIntensity],
        default = ThemeIntensity.Standard,
    ),
    cornerStyle = enumValueOrDefault(
        value = this[SettingsPreferenceKeys.CornerStyle],
        default = CornerStyle.Standard,
    ),
)

fun MutablePreferences.writeUserSettings(settings: UserSettings) {
    this[SettingsPreferenceKeys.AutomationEnabled] = settings.automationEnabled
    this[SettingsPreferenceKeys.PauseAutomationOnExternalNetworkChange] = settings.pauseAutomationOnExternalNetworkChange
    this[SettingsPreferenceKeys.TemporaryDisableMode] = settings.temporaryDisableMode.name
    this[SettingsPreferenceKeys.TemporaryDisableStartedAtMillis] = settings.temporaryDisableStartedAtMillis
    settings.temporaryDisableUntilMillis?.let {
        this[SettingsPreferenceKeys.TemporaryDisableUntilMillis] = it
    } ?: remove(SettingsPreferenceKeys.TemporaryDisableUntilMillis)
    settings.temporaryDisableForegroundPackageName?.let {
        this[SettingsPreferenceKeys.TemporaryDisableForegroundPackageName] = it
    } ?: remove(SettingsPreferenceKeys.TemporaryDisableForegroundPackageName)
    this[SettingsPreferenceKeys.NetworkControlMode] = settings.networkControlMode.name
    this[SettingsPreferenceKeys.PreferredExecutorType] = settings.preferredExecutorType.name
    this[SettingsPreferenceKeys.ScreenOffDisconnectEnabled] = settings.screenOffDisconnectEnabled
    this[SettingsPreferenceKeys.ScreenOffDelaySeconds] = settings.screenOffDelaySeconds
    this[SettingsPreferenceKeys.AppExitDisconnectEnabled] = settings.appExitDisconnectEnabled
    this[SettingsPreferenceKeys.AppExitDelaySeconds] = settings.appExitDelaySeconds
    this[SettingsPreferenceKeys.ReconnectOnTargetAppLaunch] = settings.reconnectOnTargetAppLaunch
    this[SettingsPreferenceKeys.MonitorForegroundWhenScreenOff] = settings.monitorForegroundWhenScreenOff
    this[SettingsPreferenceKeys.SkipReconnectOnWifi] = settings.skipReconnectOnWifi
    this[SettingsPreferenceKeys.SkipDisconnectOnWifi] = settings.skipDisconnectOnWifi
    this[SettingsPreferenceKeys.PreserveWifiState] = settings.preserveWifiState
    this[SettingsPreferenceKeys.PreserveBluetoothState] = settings.preserveBluetoothState
    this[SettingsPreferenceKeys.DisableScreenOnReconnect] = settings.disableScreenOnReconnect
    this[SettingsPreferenceKeys.DisableUnlockReconnect] = settings.disableUnlockReconnect
    this[SettingsPreferenceKeys.ShowReconnectPrompt] = settings.showReconnectPrompt
    this[SettingsPreferenceKeys.ReconnectPromptText] = settings.reconnectPromptText
    this[SettingsPreferenceKeys.ShowDisconnectPrompt] = settings.showDisconnectPrompt
    this[SettingsPreferenceKeys.DisconnectPromptText] = settings.disconnectPromptText
    this[SettingsPreferenceKeys.ThemeMode] = settings.themeMode.name
    this[SettingsPreferenceKeys.ThemePalette] = settings.themePalette.name
    this[SettingsPreferenceKeys.CustomSeedColorArgb] = settings.customSeedColorArgb
    this[SettingsPreferenceKeys.ThemeIntensity] = settings.themeIntensity.name
    this[SettingsPreferenceKeys.CornerStyle] = settings.cornerStyle.name
}

inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
