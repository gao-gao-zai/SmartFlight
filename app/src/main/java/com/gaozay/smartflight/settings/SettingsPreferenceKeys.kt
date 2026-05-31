package com.gaozay.smartflight.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsPreferenceKeys {
    val AutomationEnabled = booleanPreferencesKey("automation_enabled")
    val TemporaryDisableMode = stringPreferencesKey("temporary_disable_mode")
    val TemporaryDisableStartedAtMillis = longPreferencesKey("temporary_disable_started_at_millis")
    val TemporaryDisableUntilMillis = longPreferencesKey("temporary_disable_until_millis")
    val TemporaryDisableForegroundPackageName = stringPreferencesKey("temporary_disable_foreground_package_name")
    val NetworkControlMode = stringPreferencesKey("network_control_mode")
    val PreferredExecutorType = stringPreferencesKey("preferred_executor_type")
    val ScreenOffDisconnectEnabled = booleanPreferencesKey("screen_off_disconnect_enabled")
    val ScreenOffDelaySeconds = intPreferencesKey("screen_off_delay_seconds")
    val AppExitDisconnectEnabled = booleanPreferencesKey("app_exit_disconnect_enabled")
    val AppExitDelaySeconds = intPreferencesKey("app_exit_delay_seconds")
    val ReconnectOnTargetAppLaunch = booleanPreferencesKey("reconnect_on_target_app_launch")
    val MonitorForegroundWhenScreenOff = booleanPreferencesKey("monitor_foreground_when_screen_off")
    val SkipReconnectOnWifi = booleanPreferencesKey("skip_reconnect_on_wifi")
    val SkipDisconnectOnWifi = booleanPreferencesKey("skip_disconnect_on_wifi")
    val PreserveWifiState = booleanPreferencesKey("preserve_wifi_state")
    val PreserveBluetoothState = booleanPreferencesKey("preserve_bluetooth_state")
    val DisableScreenOnReconnect = booleanPreferencesKey("disable_screen_on_reconnect")
    val DisableUnlockReconnect = booleanPreferencesKey("disable_unlock_reconnect")
    val ShowReconnectPrompt = booleanPreferencesKey("show_reconnect_prompt")
    val ReconnectPromptText = stringPreferencesKey("reconnect_prompt_text")
    val ShowDisconnectPrompt = booleanPreferencesKey("show_disconnect_prompt")
    val DisconnectPromptText = stringPreferencesKey("disconnect_prompt_text")
    val ThemeMode = stringPreferencesKey("theme_mode")
    val ThemePalette = stringPreferencesKey("theme_palette")
    val CustomSeedColorArgb = intPreferencesKey("custom_seed_color_argb")
    val ThemeIntensity = stringPreferencesKey("theme_intensity")
    val CornerStyle = stringPreferencesKey("corner_style")
}
