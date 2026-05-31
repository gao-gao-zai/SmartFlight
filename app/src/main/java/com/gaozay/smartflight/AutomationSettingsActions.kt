package com.gaozay.smartflight

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AutomationSettingsActions @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val accessRepository: AccessRepository,
) {
    suspend fun setAutomationEnabled(enabled: Boolean) {
        if (enabled) {
            settingsRepository.updateSettings { it.withAutomationEnabled() }
        } else {
            disableAutomation(AutomationDisableMode.Permanent)
        }
    }

    suspend fun disableAutomation(mode: AutomationDisableMode) {
        val foregroundPackageName = runtimeStatusRepository.snapshot.first().currentForegroundPackageName
        settingsRepository.updateSettings {
            it.withAutomationDisabled(
                mode = mode,
                foregroundPackageName = foregroundPackageName,
            )
        }
    }

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        settingsRepository.updateSettings(transform)
    }

    suspend fun setNetworkControlMode(mode: NetworkControlMode) {
        updateSettings { it.copy(networkControlMode = mode) }
    }

    suspend fun setPreferredExecutorType(type: ExecutorType) {
        settingsRepository.updateSettings { current ->
            current.copy(preferredExecutorType = type)
        }
        accessRepository.refresh()
    }

    suspend fun setMonitorForegroundWhenScreenOff(enabled: Boolean) {
        settingsRepository.updateSettings { current ->
            current.copy(monitorForegroundWhenScreenOff = enabled)
        }
    }
}
