package com.gaozay.smartflight.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSettingsPreferencesMapperTest {
    @Test
    fun emptyPreferencesUseUserSettingsDefaults() {
        val settings = preferencesOf().toUserSettings()

        assertEquals(UserSettings(), settings)
    }

    @Test
    fun writeUserSettingsRoundTripsCustomizedValues() {
        val customized = UserSettings(
            automationEnabled = true,
            pauseAutomationOnExternalNetworkChange = false,
            temporaryDisableMode = AutomationDisableMode.UntilAppSwitch,
            temporaryDisableStartedAtMillis = 123L,
            temporaryDisableUntilMillis = 456L,
            temporaryDisableForegroundPackageName = "com.example.target",
            networkControlMode = NetworkControlMode.MobileData,
            preferredExecutorType = ExecutorType.Root,
            screenOffDisconnectEnabled = false,
            screenOffDelaySeconds = 12,
            appExitDisconnectEnabled = false,
            appExitDelaySeconds = 7,
            reconnectOnTargetAppLaunch = false,
            monitorForegroundWhenScreenOff = true,
            skipReconnectOnWifi = false,
            skipDisconnectOnWifi = false,
            preserveWifiState = false,
            preserveBluetoothState = false,
            disableScreenOnReconnect = false,
            disableUnlockReconnect = false,
            showReconnectPrompt = false,
            reconnectPromptText = "已回来",
            showDisconnectPrompt = false,
            disconnectPromptText = "已断开",
            themeMode = ThemeMode.Dark,
            themePalette = ThemePalette.Custom,
            customSeedColorArgb = 0xFF112233.toInt(),
            themeIntensity = ThemeIntensity.HighContrast,
            cornerStyle = CornerStyle.Soft,
            skippedUpdateVersion = "v0.0.17",
        )
        val preferences = mutablePreferencesOf()

        preferences.writeUserSettings(customized)

        assertEquals(customized, preferences.toUserSettings())
    }

    @Test
    fun writeUserSettingsRemovesNullableTemporaryDisableValuesWhenUnset() {
        val preferences = mutablePreferencesOf()
        preferences.writeUserSettings(
            UserSettings(
                temporaryDisableUntilMillis = 456L,
                temporaryDisableForegroundPackageName = "com.example.target",
            ),
        )

        preferences.writeUserSettings(UserSettings())

        assertNull(preferences[SettingsPreferenceKeys.TemporaryDisableUntilMillis])
        assertNull(preferences[SettingsPreferenceKeys.TemporaryDisableForegroundPackageName])
    }

    @Test
    fun writeUserSettingsRemovesSkippedUpdateVersionWhenUnset() {
        val preferences = mutablePreferencesOf()
        preferences.writeUserSettings(UserSettings(skippedUpdateVersion = "v0.0.17"))

        preferences.writeUserSettings(UserSettings())

        assertNull(preferences[SettingsPreferenceKeys.SkippedUpdateVersion])
    }

    @Test
    fun invalidEnumPreferencesFallBackToDefaults() {
        val settings = preferencesOf(
            SettingsPreferenceKeys.TemporaryDisableMode to "invalid-disable-mode",
            SettingsPreferenceKeys.NetworkControlMode to "invalid-network-mode",
            SettingsPreferenceKeys.PreferredExecutorType to "invalid-executor",
            SettingsPreferenceKeys.ThemeMode to "invalid-theme-mode",
            SettingsPreferenceKeys.ThemePalette to "invalid-theme-palette",
            SettingsPreferenceKeys.ThemeIntensity to "invalid-theme-intensity",
            SettingsPreferenceKeys.CornerStyle to "invalid-corner-style",
        ).toUserSettings()

        assertEquals(AutomationDisableMode.None, settings.temporaryDisableMode)
        assertEquals(NetworkControlMode.AirplaneMode, settings.networkControlMode)
        assertEquals(ExecutorType.Auto, settings.preferredExecutorType)
        assertEquals(ThemeMode.System, settings.themeMode)
        assertEquals(ThemePalette.LogoOriginal, settings.themePalette)
        assertEquals(ThemeIntensity.Standard, settings.themeIntensity)
        assertEquals(CornerStyle.Standard, settings.cornerStyle)
    }
}
