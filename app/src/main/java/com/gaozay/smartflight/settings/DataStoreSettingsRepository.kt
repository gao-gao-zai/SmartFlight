package com.gaozay.smartflight.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smartflight_settings",
)

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = context.settingsDataStore.data.map { preferences ->
        UserSettings(
            automationEnabled = preferences[Keys.AutomationEnabled] ?: false,
            temporaryDisableMode = enumValueOrDefault(
                value = preferences[Keys.TemporaryDisableMode],
                default = AutomationDisableMode.None,
            ),
            temporaryDisableStartedAtMillis = preferences[Keys.TemporaryDisableStartedAtMillis] ?: 0L,
            temporaryDisableUntilMillis = preferences[Keys.TemporaryDisableUntilMillis],
            temporaryDisableForegroundPackageName = preferences[Keys.TemporaryDisableForegroundPackageName],
            networkControlMode = enumValueOrDefault(
                value = preferences[Keys.NetworkControlMode],
                default = NetworkControlMode.AirplaneMode,
            ),
            preferredExecutorType = enumValueOrDefault(
                value = preferences[Keys.PreferredExecutorType],
                default = ExecutorType.Auto,
            ),
            screenOffDisconnectEnabled = preferences[Keys.ScreenOffDisconnectEnabled] ?: true,
            screenOffDelaySeconds = preferences[Keys.ScreenOffDelaySeconds] ?: 60,
            appExitDisconnectEnabled = preferences[Keys.AppExitDisconnectEnabled] ?: true,
            appExitDelaySeconds = preferences[Keys.AppExitDelaySeconds] ?: 30,
            reconnectOnTargetAppLaunch = preferences[Keys.ReconnectOnTargetAppLaunch] ?: true,
            monitorForegroundWhenScreenOff = preferences[Keys.MonitorForegroundWhenScreenOff] ?: false,
            skipReconnectOnWifi = preferences[Keys.SkipReconnectOnWifi] ?: true,
            skipDisconnectOnWifi = preferences[Keys.SkipDisconnectOnWifi] ?: true,
            preserveWifiState = preferences[Keys.PreserveWifiState] ?: true,
            preserveBluetoothState = preferences[Keys.PreserveBluetoothState] ?: true,
            disableScreenOnReconnect = preferences[Keys.DisableScreenOnReconnect] ?: true,
            disableUnlockReconnect = preferences[Keys.DisableUnlockReconnect] ?: true,
            showReconnectPrompt = preferences[Keys.ShowReconnectPrompt] ?: true,
            reconnectPromptText = preferences[Keys.ReconnectPromptText] ?: "SmartFlight 已恢复联网",
            showDisconnectPrompt = preferences[Keys.ShowDisconnectPrompt] ?: true,
            disconnectPromptText = preferences[Keys.DisconnectPromptText] ?: "SmartFlight 已断网",
            themeMode = enumValueOrDefault(
                value = preferences[Keys.ThemeMode],
                default = ThemeMode.System,
            ),
            themePalette = enumValueOrDefault(
                value = preferences[Keys.ThemePalette],
                default = ThemePalette.LogoOriginal,
            ),
            customSeedColorArgb = preferences[Keys.CustomSeedColorArgb] ?: ThemePalette.LogoOriginal.seedColorArgb,
            themeIntensity = enumValueOrDefault(
                value = preferences[Keys.ThemeIntensity],
                default = ThemeIntensity.Standard,
            ),
            cornerStyle = enumValueOrDefault(
                value = preferences[Keys.CornerStyle],
                default = CornerStyle.Standard,
            ),
        )
    }

    override suspend fun setAutomationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutomationEnabled] = enabled
        }
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(settings.first())
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutomationEnabled] = updated.automationEnabled
            preferences[Keys.TemporaryDisableMode] = updated.temporaryDisableMode.name
            preferences[Keys.TemporaryDisableStartedAtMillis] = updated.temporaryDisableStartedAtMillis
            updated.temporaryDisableUntilMillis?.let {
                preferences[Keys.TemporaryDisableUntilMillis] = it
            } ?: preferences.remove(Keys.TemporaryDisableUntilMillis)
            updated.temporaryDisableForegroundPackageName?.let {
                preferences[Keys.TemporaryDisableForegroundPackageName] = it
            } ?: preferences.remove(Keys.TemporaryDisableForegroundPackageName)
            preferences[Keys.NetworkControlMode] = updated.networkControlMode.name
            preferences[Keys.PreferredExecutorType] = updated.preferredExecutorType.name
            preferences[Keys.ScreenOffDisconnectEnabled] = updated.screenOffDisconnectEnabled
            preferences[Keys.ScreenOffDelaySeconds] = updated.screenOffDelaySeconds
            preferences[Keys.AppExitDisconnectEnabled] = updated.appExitDisconnectEnabled
            preferences[Keys.AppExitDelaySeconds] = updated.appExitDelaySeconds
            preferences[Keys.ReconnectOnTargetAppLaunch] = updated.reconnectOnTargetAppLaunch
            preferences[Keys.MonitorForegroundWhenScreenOff] = updated.monitorForegroundWhenScreenOff
            preferences[Keys.SkipReconnectOnWifi] = updated.skipReconnectOnWifi
            preferences[Keys.SkipDisconnectOnWifi] = updated.skipDisconnectOnWifi
            preferences[Keys.PreserveWifiState] = updated.preserveWifiState
            preferences[Keys.PreserveBluetoothState] = updated.preserveBluetoothState
            preferences[Keys.DisableScreenOnReconnect] = updated.disableScreenOnReconnect
            preferences[Keys.DisableUnlockReconnect] = updated.disableUnlockReconnect
            preferences[Keys.ShowReconnectPrompt] = updated.showReconnectPrompt
            preferences[Keys.ReconnectPromptText] = updated.reconnectPromptText
            preferences[Keys.ShowDisconnectPrompt] = updated.showDisconnectPrompt
            preferences[Keys.DisconnectPromptText] = updated.disconnectPromptText
            preferences[Keys.ThemeMode] = updated.themeMode.name
            preferences[Keys.ThemePalette] = updated.themePalette.name
            preferences[Keys.CustomSeedColorArgb] = updated.customSeedColorArgb
            preferences[Keys.ThemeIntensity] = updated.themeIntensity.name
            preferences[Keys.CornerStyle] = updated.cornerStyle.name
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object Keys {
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
}
