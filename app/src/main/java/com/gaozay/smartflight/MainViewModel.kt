package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    val uiState = SmartFlightUiState()
}

@Immutable
data class SmartFlightUiState(
    val advancedAccess: String = "Shizuku / ADB / Root required",
    val currentMode: String = "Airplane Mode",
    val foregroundApp: String = "Not connected yet",
    val triggerSummary: String = "Project initialized. Runtime engine pending.",
)

