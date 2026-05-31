package com.gaozay.smartflight.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
        preferences.toUserSettings()
    }

    override suspend fun setAutomationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.AutomationEnabled] = enabled
        }
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(settings.first())
        context.settingsDataStore.edit { preferences ->
            preferences.writeUserSettings(updated)
        }
    }
}
