package com.gaozay.smartflight.permission

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adbBootstrapDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "adb_bootstrap_state",
)

@Singleton
class DataStoreAdbBootstrapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdbBootstrapRepository {
    override suspend fun getSnapshot(): AdbBootstrapSnapshot {
        val preferences = context.adbBootstrapDataStore.data.first()
        return AdbBootstrapSnapshot(
            bootstrapped = preferences[Keys.isBootstrapped] ?: false,
            completedAtMillis = preferences[Keys.completedAtMillis] ?: 0L,
            commandVersion = preferences[Keys.commandVersion] ?: 1,
        )
    }

    override suspend fun setBootstrapped(bootstrapped: Boolean) {
        context.adbBootstrapDataStore.edit { preferences ->
            preferences[Keys.isBootstrapped] = bootstrapped
            preferences[Keys.completedAtMillis] = if (bootstrapped) System.currentTimeMillis() else 0L
            preferences[Keys.commandVersion] = 1
        }
    }

    private object Keys {
        val isBootstrapped = booleanPreferencesKey("is_bootstrapped")
        val completedAtMillis = longPreferencesKey("completed_at_millis")
        val commandVersion = intPreferencesKey("command_version")
    }
}
