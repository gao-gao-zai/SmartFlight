package com.gaozay.smartflight.runtime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.domain.model.UnifiedNetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.runtimeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smartflight_runtime",
)

@Singleton
class DataStoreRuntimeStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : RuntimeStatusRepository {
    override val snapshot: Flow<RuntimeSnapshot> = context.runtimeDataStore.data.map { preferences ->
        RuntimeSnapshot(
            currentForegroundPackageName = preferences[Keys.CurrentForegroundPackageName],
            currentForegroundAppLabel = preferences[Keys.CurrentForegroundAppLabel],
            screenState = enumValueOrDefault(
                value = preferences[Keys.ScreenState],
                default = ScreenState.Unknown,
            ),
            unifiedNetworkState = enumValueOrDefault(
                value = preferences[Keys.UnifiedNetworkState],
                default = UnifiedNetworkState.Unknown,
            ),
            isAirplaneModeEnabled = preferences[Keys.IsAirplaneModeEnabled],
            isMobileDataEnabled = preferences[Keys.IsMobileDataEnabled],
            isWifiConnected = preferences[Keys.IsWifiConnected] ?: false,
            isWifiEnabled = preferences[Keys.IsWifiEnabled] ?: false,
            isBluetoothEnabled = preferences[Keys.IsBluetoothEnabled] ?: false,
            isBluetoothStateReadable = preferences[Keys.IsBluetoothStateReadable] ?: false,
            isForegroundServiceRunning = preferences[Keys.IsForegroundServiceRunning] ?: false,
            isScreenOffDisconnectScheduled = preferences[Keys.IsScreenOffDisconnectScheduled] ?: false,
            pendingScreenOffDisconnectAtMillis = preferences[Keys.PendingScreenOffDisconnectAtMillis],
            isAppExitDisconnectScheduled = preferences[Keys.IsAppExitDisconnectScheduled] ?: false,
            pendingAppExitDisconnectAtMillis = preferences[Keys.PendingAppExitDisconnectAtMillis],
            lastAction = enumValueOrDefault(
                value = preferences[Keys.LastAction],
                default = ExecutionAction.DoNothing,
            ),
            lastTriggerSource = enumValueOrDefault(
                value = preferences[Keys.LastTriggerSource],
                default = TriggerSource.ServiceRestored,
            ),
            lastActionResult = enumValueOrDefault(
                value = preferences[Keys.LastActionResult],
                default = ExecutionResult.Pending,
            ),
            lastActionReason = preferences[Keys.LastActionReason] ?: "Runtime not started yet",
            runtimeStatusResult = enumValueOrDefault(
                value = preferences[Keys.RuntimeStatusResult],
                default = ExecutionResult.Pending,
            ),
            runtimeStatusSummary = preferences[Keys.RuntimeStatusSummary] ?: "尚未执行自检",
            activeExecutorType = enumValueOrDefault(
                value = preferences[Keys.ActiveExecutorType],
                default = ExecutorType.Unavailable,
            ),
            updatedAtMillis = preferences[Keys.UpdatedAtMillis] ?: 0,
        )
    }

    override suspend fun updateSnapshot(transform: (RuntimeSnapshot) -> RuntimeSnapshot) {
        val updated = transform(snapshot.first()).withDerivedUnifiedNetworkState()
        context.runtimeDataStore.edit { preferences ->
            updated.currentForegroundPackageName?.let {
                preferences[Keys.CurrentForegroundPackageName] = it
            } ?: preferences.remove(Keys.CurrentForegroundPackageName)
            updated.currentForegroundAppLabel?.let {
                preferences[Keys.CurrentForegroundAppLabel] = it
            } ?: preferences.remove(Keys.CurrentForegroundAppLabel)
            preferences[Keys.ScreenState] = updated.screenState.name
            preferences[Keys.UnifiedNetworkState] = updated.unifiedNetworkState.name
            updated.isAirplaneModeEnabled?.let {
                preferences[Keys.IsAirplaneModeEnabled] = it
            } ?: preferences.remove(Keys.IsAirplaneModeEnabled)
            updated.isMobileDataEnabled?.let {
                preferences[Keys.IsMobileDataEnabled] = it
            } ?: preferences.remove(Keys.IsMobileDataEnabled)
            preferences[Keys.IsWifiConnected] = updated.isWifiConnected
            preferences[Keys.IsWifiEnabled] = updated.isWifiEnabled
            preferences[Keys.IsBluetoothEnabled] = updated.isBluetoothEnabled
            preferences[Keys.IsBluetoothStateReadable] = updated.isBluetoothStateReadable
            preferences[Keys.IsForegroundServiceRunning] = updated.isForegroundServiceRunning
            preferences[Keys.IsScreenOffDisconnectScheduled] = updated.isScreenOffDisconnectScheduled
            updated.pendingScreenOffDisconnectAtMillis?.let {
                preferences[Keys.PendingScreenOffDisconnectAtMillis] = it
            } ?: preferences.remove(Keys.PendingScreenOffDisconnectAtMillis)
            preferences[Keys.IsAppExitDisconnectScheduled] = updated.isAppExitDisconnectScheduled
            updated.pendingAppExitDisconnectAtMillis?.let {
                preferences[Keys.PendingAppExitDisconnectAtMillis] = it
            } ?: preferences.remove(Keys.PendingAppExitDisconnectAtMillis)
            preferences[Keys.LastAction] = updated.lastAction.name
            preferences[Keys.LastTriggerSource] = updated.lastTriggerSource.name
            preferences[Keys.LastActionResult] = updated.lastActionResult.name
            preferences[Keys.LastActionReason] = updated.lastActionReason
            preferences[Keys.RuntimeStatusResult] = updated.runtimeStatusResult.name
            preferences[Keys.RuntimeStatusSummary] = updated.runtimeStatusSummary
            preferences[Keys.ActiveExecutorType] = updated.activeExecutorType.name
            preferences[Keys.UpdatedAtMillis] = updated.updatedAtMillis
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object Keys {
        val CurrentForegroundPackageName = stringPreferencesKey("current_foreground_package_name")
        val CurrentForegroundAppLabel = stringPreferencesKey("current_foreground_app_label")
        val ScreenState = stringPreferencesKey("screen_state")
        val UnifiedNetworkState = stringPreferencesKey("unified_network_state")
        val IsAirplaneModeEnabled = booleanPreferencesKey("is_airplane_mode_enabled")
        val IsMobileDataEnabled = booleanPreferencesKey("is_mobile_data_enabled")
        val IsWifiConnected = booleanPreferencesKey("is_wifi_connected")
        val IsWifiEnabled = booleanPreferencesKey("is_wifi_enabled")
        val IsBluetoothEnabled = booleanPreferencesKey("is_bluetooth_enabled")
        val IsBluetoothStateReadable = booleanPreferencesKey("is_bluetooth_state_readable")
        val IsForegroundServiceRunning = booleanPreferencesKey("is_foreground_service_running")
        val IsScreenOffDisconnectScheduled = booleanPreferencesKey("is_screen_off_disconnect_scheduled")
        val PendingScreenOffDisconnectAtMillis = longPreferencesKey("pending_screen_off_disconnect_at_millis")
        val IsAppExitDisconnectScheduled = booleanPreferencesKey("is_app_exit_disconnect_scheduled")
        val PendingAppExitDisconnectAtMillis = longPreferencesKey("pending_app_exit_disconnect_at_millis")
        val LastAction = stringPreferencesKey("last_action")
        val LastTriggerSource = stringPreferencesKey("last_trigger_source")
        val LastActionResult = stringPreferencesKey("last_action_result")
        val LastActionReason = stringPreferencesKey("last_action_reason")
        val RuntimeStatusResult = stringPreferencesKey("runtime_status_result")
        val RuntimeStatusSummary = stringPreferencesKey("runtime_status_summary")
        val ActiveExecutorType = stringPreferencesKey("active_executor_type")
        val UpdatedAtMillis = longPreferencesKey("updated_at_millis")
    }
}
