package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    runtimeStatusRepository: RuntimeStatusRepository,
    installedAppRepository: InstalledAppRepository,
    executionLogRepository: ExecutionLogRepository,
    private val accessRepository: AccessRepository,
) : ViewModel() {
    val uiState: StateFlow<SmartFlightUiState> = combine(
        settingsRepository.settings,
        runtimeStatusRepository.snapshot,
        installedAppRepository.observeAppCount(),
        executionLogRepository.observeLogCount(),
        accessRepository.accessGateState,
    ) { settings, runtimeSnapshot, appCount, logCount, accessGateState ->
        SmartFlightUiState(
            accessGateState = accessGateState,
            advancedAccess = accessGateState.advancedAccess.selectedExecutorType.label,
            currentMode = settings.networkControlMode.label,
            foregroundApp = runtimeSnapshot.currentForegroundAppLabel
                ?: runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            triggerSummary = buildString {
                append(runtimeSnapshot.lastActionReason)
                append(" · Apps: ")
                append(appCount)
                append(" · Logs: ")
                append(logCount)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartFlightUiState(),
    )

    init {
        refreshAccessChecks()
    }

    fun refreshAccessChecks() {
        viewModelScope.launch {
            accessRepository.refresh()
        }
    }
}

@Immutable
data class SmartFlightUiState(
    val accessGateState: AccessGateState = AccessGateState(),
    val advancedAccess: String = "Shizuku / ADB / Root required",
    val currentMode: String = "Airplane Mode",
    val foregroundApp: String = "Not connected yet",
    val triggerSummary: String = "Project initialized. Runtime engine pending.",
)

