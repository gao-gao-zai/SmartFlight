package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AutomationTileController(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val scope: CoroutineScope,
    private val renderMode: (AutomationDisableMode) -> Unit,
    private val ensureAutomationServiceRunning: () -> Unit,
) {
    private var listeningJob: Job? = null
    private val clickMutex = Mutex()

    fun startListening() {
        stopListening()
        listeningJob = scope.launch {
            settingsRepository.settings
                .map { it.currentTileMode() }
                .distinctUntilChanged()
                .collect { renderMode(it) }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
    }

    fun refresh() {
        // Re-emit the current mode for a locale/configuration change, but never
        // update a Tile outside its listening window.
        if (listeningJob != null) {
            startListening()
        }
    }

    suspend fun onClick() {
        // Queue every click, including clicks received while a write is pending.
        clickMutex.withLock {
            val foregroundPackageName = runtimeStatusRepository.snapshot.first().currentForegroundPackageName
            settingsRepository.updateSettings { current ->
                val nextMode = current.currentTileMode().nextTileMode()
                if (nextMode == AutomationDisableMode.None) {
                    current.withAutomationEnabled()
                } else {
                    current.withAutomationDisabled(
                        mode = nextMode,
                        foregroundPackageName = foregroundPackageName,
                    )
                }
            }
            // Rendering follows persisted settings, independently of service startup.
            ensureAutomationServiceRunning()
        }
    }
}

private fun UserSettings.currentTileMode(): AutomationDisableMode = when {
    !automationEnabled -> AutomationDisableMode.Permanent
    temporaryDisableMode != AutomationDisableMode.None -> temporaryDisableMode
    else -> AutomationDisableMode.None
}

private fun AutomationDisableMode.nextTileMode(): AutomationDisableMode {
    val currentIndex = tileModeCycle.indexOf(this).takeIf { it >= 0 } ?: 0
    return tileModeCycle[(currentIndex + 1) % tileModeCycle.size]
}

private val tileModeCycle = listOf(
    AutomationDisableMode.None,
    AutomationDisableMode.UntilAppSwitch,
    AutomationDisableMode.UntilScreenOff,
    AutomationDisableMode.For1Minute,
    AutomationDisableMode.For5Minutes,
    AutomationDisableMode.For10Minutes,
    AutomationDisableMode.For20Minutes,
    AutomationDisableMode.For30Minutes,
    AutomationDisableMode.Permanent,
)
