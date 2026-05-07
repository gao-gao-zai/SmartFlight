package com.gaozay.smartflight.permission

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rootProbeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "root_probe_state",
)

@Singleton
class DataStoreRootAccessProbeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : RootAccessProbeRepository {
    override suspend fun getSnapshot(): RootProbeSnapshot {
        val preferences = context.rootProbeDataStore.data.first()
        return RootProbeSnapshot(
            confirmedAvailable = preferences[Keys.confirmedAvailable] ?: false,
            lastProbeAtMillis = preferences[Keys.lastProbeAtMillis] ?: 0L,
            lastProbeSummary = preferences[Keys.lastProbeSummary].orEmpty(),
        )
    }

    override suspend fun updateSnapshot(snapshot: RootProbeSnapshot) {
        context.rootProbeDataStore.edit { preferences ->
            preferences[Keys.confirmedAvailable] = snapshot.confirmedAvailable
            preferences[Keys.lastProbeAtMillis] = snapshot.lastProbeAtMillis
            preferences[Keys.lastProbeSummary] = snapshot.lastProbeSummary
        }
    }

    private object Keys {
        val confirmedAvailable = booleanPreferencesKey("confirmed_available")
        val lastProbeAtMillis = longPreferencesKey("last_probe_at_millis")
        val lastProbeSummary = stringPreferencesKey("last_probe_summary")
    }
}
