package com.gaozay.smartflight.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setAutomationEnabled(enabled: Boolean)

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
}
