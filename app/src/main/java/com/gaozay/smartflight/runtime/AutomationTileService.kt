package com.gaozay.smartflight.runtime

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.withAutomationDisabled
import com.gaozay.smartflight.settings.withAutomationEnabled
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutomationTileService : TileService() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var runtimeStatusRepository: RuntimeStatusRepository

    @Inject
    lateinit var automationServiceController: AutomationServiceController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val settings = settingsRepository.settings.first()
            val nextMode = settings.currentTileMode().nextTileMode()
            if (nextMode == AutomationDisableMode.None) {
                settingsRepository.updateSettings { it.withAutomationEnabled() }
                automationServiceController.setAutomationEnabled(true)
            } else {
                automationServiceController.setAutomationEnabled(true)
                val foregroundPackageName = runtimeStatusRepository.snapshot.first().currentForegroundPackageName
                settingsRepository.updateSettings {
                    it.withAutomationDisabled(
                        mode = nextMode,
                        foregroundPackageName = foregroundPackageName,
                    )
                }
            }
            refreshTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val tileMode = settings.currentTileMode()
            qsTile?.apply {
                label = "自动飞行"
                subtitle = tileMode.tileLabel
                state = if (tileMode == AutomationDisableMode.None) {
                    Tile.STATE_INACTIVE
                } else {
                    Tile.STATE_ACTIVE
                }
                updateTile()
            }
        }
    }

    private fun com.gaozay.smartflight.settings.UserSettings.currentTileMode(): AutomationDisableMode =
        when {
            !automationEnabled -> AutomationDisableMode.Permanent
            temporaryDisableMode != AutomationDisableMode.None -> temporaryDisableMode
            else -> AutomationDisableMode.None
        }

    private fun AutomationDisableMode.nextTileMode(): AutomationDisableMode {
        val currentIndex = tileModeCycle.indexOf(this).takeIf { it >= 0 } ?: 0
        return tileModeCycle[(currentIndex + 1) % tileModeCycle.size]
    }

    private companion object {
        val tileModeCycle = listOf(
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
    }
}
