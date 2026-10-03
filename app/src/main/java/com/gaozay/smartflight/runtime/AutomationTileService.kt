package com.gaozay.smartflight.runtime

import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gaozay.smartflight.R
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AutomationTileService : TileService() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var runtimeStatusRepository: RuntimeStatusRepository

    @Inject
    lateinit var automationServiceController: AutomationServiceController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy {
        AutomationTileController(
            settingsRepository = settingsRepository,
            runtimeStatusRepository = runtimeStatusRepository,
            scope = scope,
            renderMode = ::renderTile,
            ensureAutomationServiceRunning = { automationServiceController.setAutomationEnabled(true) },
        )
    }

    override fun onStartListening() {
        super.onStartListening()
        controller.startListening()
    }

    override fun onStopListening() {
        controller.stopListening()
        super.onStopListening()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.refresh()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            controller.onClick()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderTile(tileMode: AutomationDisableMode) {
        qsTile?.apply {
            icon = Icon.createWithResource(this@AutomationTileService, R.drawable.ic_smartflight_tile)
            label = getString(R.string.automation_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(tileMode.tileLabelRes)
            }
            state = if (tileMode == AutomationDisableMode.None) {
                Tile.STATE_INACTIVE
            } else {
                Tile.STATE_ACTIVE
            }
            updateTile()
        }
    }
}
