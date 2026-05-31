package com.gaozay.smartflight.runtime

import android.content.Context
import android.widget.Toast
import com.gaozay.smartflight.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RuntimePromptNotifier {
    suspend fun showReconnectPrompt(settings: UserSettings)

    suspend fun showDisconnectPrompt(settings: UserSettings)

    suspend fun showAutomationRestoredPrompt(settings: UserSettings, reason: String)
}

class ToastRuntimePromptNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RuntimePromptNotifier {
    override suspend fun showReconnectPrompt(settings: UserSettings) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(settings.reconnectPromptText.ifBlank { "SmartFlight 已恢复联网" })
    }

    override suspend fun showDisconnectPrompt(settings: UserSettings) {
        if (!settings.showDisconnectPrompt) {
            return
        }
        showPrompt(settings.disconnectPromptText.ifBlank { "SmartFlight 已断网" })
    }

    override suspend fun showAutomationRestoredPrompt(settings: UserSettings, reason: String) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(reason.ifBlank { "SmartFlight 已恢复自动化" })
    }

    private suspend fun showPrompt(message: String) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
