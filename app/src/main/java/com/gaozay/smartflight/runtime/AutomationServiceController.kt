package com.gaozay.smartflight.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun setAutomationEnabled(enabled: Boolean) {
        if (enabled) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = AutomationForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(Intent(context, AutomationForegroundService::class.java))
        }
    }
}
