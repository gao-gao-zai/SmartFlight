package com.gaozay.smartflight.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ScreenState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class AutomationForegroundService : Service() {
    @Inject
    lateinit var runtimeCoordinator: AutomationRuntimeCoordinator

    private var currentScreenState: ScreenState = ScreenState.Unknown

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(LOG_TAG, "screen broadcast action=$action currentScreenState=$currentScreenState")
            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    currentScreenState = ScreenState.ScreenOff
                    runtimeCoordinator.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    currentScreenState = ScreenState.ScreenOn
                    runtimeCoordinator.onScreenOn()
                }
                Intent.ACTION_USER_PRESENT -> {
                    currentScreenState = ScreenState.Unlocked
                    runtimeCoordinator.onUserUnlocked()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentScreenState = detectCurrentScreenState()
        Log.d(LOG_TAG, "service onCreate screenState=$currentScreenState")
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "service onStartCommand action=${intent?.action} startId=$startId screenState=$currentScreenState")
        runtimeCoordinator.start(currentScreenState)
        when (intent?.action) {
            ACTION_DEBUG_SCREEN_OFF -> {
                currentScreenState = ScreenState.ScreenOff
                runtimeCoordinator.onScreenOff()
            }
            ACTION_DEBUG_SCREEN_ON -> {
                currentScreenState = ScreenState.ScreenOn
                runtimeCoordinator.onScreenOn()
            }
            else -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "service onDestroy screenState=$currentScreenState")
        runBlocking {
            runtimeCoordinator.stop(currentScreenState)
        }
        runCatching { unregisterReceiver(screenStateReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun detectCurrentScreenState(): ScreenState {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (powerManager.isInteractive) ScreenState.ScreenOn else ScreenState.ScreenOff
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.automation_notification_title))
            .setContentText(getString(R.string.automation_notification_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.automation_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.automation_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val LOG_TAG = "SmartFlightRuntime"
        private const val NOTIFICATION_CHANNEL_ID = "automation_runtime"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.gaozay.smartflight.runtime.action.START"
        const val ACTION_DEBUG_SCREEN_OFF = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_OFF"
        const val ACTION_DEBUG_SCREEN_ON = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_ON"
    }
}
