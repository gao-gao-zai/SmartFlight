package com.gaozay.smartflight.quickrule

import android.annotation.SuppressLint
import android.content.Intent
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gaozay.smartflight.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class QuickRuleTileService : TileService() {
    @Inject lateinit var targets: QuickRuleTargetResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var launching = false
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            icon = Icon.createWithResource(this@QuickRuleTileService, R.drawable.ic_quick_rule_tile)
            label = getString(R.string.quick_rule_tile_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (launching) return
        launching = true
        if (isLocked) unlockAndRun { launchDialog() } else launchDialog()
    }
    private fun launchDialog() {
        scope.launch {
            try {
                val target = try { targets.capture() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                val intent = QuickRuleActivity.intent(this@QuickRuleTileService, target)
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(PendingIntent.getActivity(this@QuickRuleTileService, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                } else {
                    launchOnLegacyAndroid(intent)
                }
            } finally { launching = false }
        }
    }
    // The PendingIntent overload was added in API 34; API 26–33 require the old method.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchOnLegacyAndroid(intent: Intent) { startActivityAndCollapse(intent) }

    override fun onStopListening() { launching = false; super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
