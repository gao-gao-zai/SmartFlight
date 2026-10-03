package com.gaozay.smartflight.runtime

import android.content.Context
import android.widget.Toast
import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface RuntimePromptNotifier {
    suspend fun showReconnectPrompt(settings: UserSettings)

    suspend fun showDisconnectPrompt(settings: UserSettings)

    suspend fun showAutomationPausedPrompt(settings: UserSettings, reason: String)

    suspend fun showAutomationRestoredPrompt(settings: UserSettings, reason: String)
}

class ToastRuntimePromptNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RuntimePromptNotifier {
    override suspend fun showReconnectPrompt(settings: UserSettings) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(settings.reconnectPromptText.ifBlank { AppStrings.get(R.string.prompt_reconnected_default) })
    }

    override suspend fun showDisconnectPrompt(settings: UserSettings) {
        if (!settings.showDisconnectPrompt) {
            return
        }
        showPrompt(settings.disconnectPromptText.ifBlank { AppStrings.get(R.string.prompt_disconnected_default) })
    }

    override suspend fun showAutomationRestoredPrompt(settings: UserSettings, reason: String) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(reason.ifBlank { AppStrings.get(R.string.smartflight_restored_automation) })
    }

    override suspend fun showAutomationPausedPrompt(settings: UserSettings, reason: String) {
        if (!settings.showDisconnectPrompt) {
            return
        }
        showPrompt(reason.ifBlank { AppStrings.get(R.string.smartflight_paused_automation) })
    }

    private suspend fun showPrompt(message: String) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
