package com.gaozay.smartflight.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.status
import com.gaozay.smartflight.domain.model.AppListStatus
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AutomationForegroundService : Service() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var runtimeStatusRepository: RuntimeStatusRepository

    @Inject
    lateinit var installedAppRepository: InstalledAppRepository

    @Inject
    lateinit var accessRepository: AccessRepository

    @Inject
    lateinit var foregroundAppDetector: ForegroundAppDetector

    @Inject
    lateinit var automationRuleEngine: AutomationRuleEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var automationJob: Job? = null
    private var screenOffDisconnectJob: Job? = null
    private var appExitDisconnectJob: Job? = null
    private var lastTargetAppActive: Boolean? = null
    private var appRulesByPackageName: Map<String, AppRuntimeRuleInfo> = emptyMap()
    private var currentScreenState: ScreenState = ScreenState.Unknown
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            currentScreenState = when (action) {
                Intent.ACTION_SCREEN_OFF -> ScreenState.ScreenOff
                Intent.ACTION_USER_PRESENT -> ScreenState.Unlocked
                Intent.ACTION_SCREEN_ON -> ScreenState.ScreenOn
                else -> currentScreenState
            }
            serviceScope.launch {
                when (action) {
                    Intent.ACTION_SCREEN_OFF -> scheduleScreenOffDisconnectIfNeeded()
                    Intent.ACTION_SCREEN_ON -> cancelScreenOffDisconnect()

                    Intent.ACTION_USER_PRESENT -> cancelScreenOffDisconnect()
                }
                runtimeStatusRepository.updateSnapshot { snapshot ->
                    snapshot.copy(
                        screenState = currentScreenState,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentScreenState = detectCurrentScreenState()
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
        when (intent?.action) {
            ACTION_DEBUG_SCREEN_OFF -> {
                currentScreenState = ScreenState.ScreenOff
                serviceScope.launch {
                    scheduleScreenOffDisconnectIfNeeded()
                    runtimeStatusRepository.updateSnapshot { snapshot ->
                        snapshot.copy(
                            screenState = currentScreenState,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                }
            }

            ACTION_DEBUG_SCREEN_ON -> {
                currentScreenState = ScreenState.ScreenOn
                serviceScope.launch {
                    cancelScreenOffDisconnect()
                    runtimeStatusRepository.updateSnapshot { snapshot ->
                        snapshot.copy(
                            screenState = currentScreenState,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
        startAutomationLoopIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        automationJob?.cancel()
        screenOffDisconnectJob?.cancel()
        appExitDisconnectJob?.cancel()
        unregisterReceiver(screenStateReceiver)
        runBlocking {
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isForegroundServiceRunning = false,
                    isScreenOffDisconnectScheduled = false,
                    pendingScreenOffDisconnectAtMillis = null,
                    isAppExitDisconnectScheduled = false,
                    pendingAppExitDisconnectAtMillis = null,
                    screenState = currentScreenState,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAutomationLoopIfNeeded() {
        if (automationJob?.isActive == true) {
            return
        }
        automationJob = serviceScope.launch {
            launch {
                installedAppRepository.observeApps().collect { apps ->
                    appRulesByPackageName = apps.associate { app ->
                        app.packageName to AppRuntimeRuleInfo(
                            status = app.status(),
                            isCandidate = app.isCandidate,
                        )
                    }
                }
            }
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isForegroundServiceRunning = true,
                    screenState = currentScreenState,
                    lastTriggerSource = TriggerSource.ServiceRestored,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            while (isActive) {
                val enabled = settingsRepository.settings.first().automationEnabled
                if (!enabled) {
                    stopSelf()
                    return@launch
                }
                runCatching {
                    automationTick()
                }.onFailure { throwable ->
                    runtimeStatusRepository.updateSnapshot { snapshot ->
                        snapshot.copy(
                            lastTriggerSource = TriggerSource.ServiceRestored,
                            lastActionResult = ExecutionResult.Failed,
                            lastActionReason = "自动化轮询失败：${throwable.message ?: "未知错误"}",
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                }
                delay(
                    automationRuleEngine.nextPollIntervalMillis(
                        settings = settingsRepository.settings.first(),
                        screenState = currentScreenState,
                    ),
                )
            }
        }
    }

    private suspend fun automationTick() {
        val settings = settingsRepository.settings.first()
        if (!automationRuleEngine.shouldMonitorForeground(settings, currentScreenState)) {
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    screenState = currentScreenState,
                    isForegroundServiceRunning = true,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }
        val foregroundApp = foregroundAppDetector.detect()
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                currentForegroundPackageName = foregroundApp?.packageName,
                currentForegroundAppLabel = foregroundApp?.appLabel,
                screenState = currentScreenState,
                isForegroundServiceRunning = true,
                updatedAtMillis = if (foregroundApp != null) System.currentTimeMillis() else snapshot.updatedAtMillis,
            )
        }

        val packageName = foregroundApp?.packageName
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val appRuleInfo = packageName?.let { appRulesByPackageName[it] }
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val decision = automationRuleEngine.evaluateForegroundChange(
            ForegroundRuleContext(
                settings = settings,
                packageName = packageName,
                appLabel = foregroundApp?.appLabel,
                appStatus = appRuleInfo?.status,
                isCandidate = appRuleInfo?.isCandidate == true,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
                previousTargetAppActive = lastTargetAppActive,
            ),
        )
        lastTargetAppActive = decision.targetAppActive

        when (val action = decision.action) {
            is ForegroundAction.None -> {
                if (decision.shouldLog) {
                    updateSkippedForegroundDecision(decision)
                }
            }
            is ForegroundAction.Reconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = true)
                executeAirplaneModeChange(
                    currentEnabled = runtimeSnapshot.isAirplaneModeEnabled,
                    targetEnabled = false,
                    triggerSource = TriggerSource.AppForegroundChanged,
                    reason = action.reason,
                    onPrompt = { showReconnectPrompt(settings) },
                )
            }

            is ForegroundAction.Disconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = false)
                executeAirplaneModeChange(
                    currentEnabled = runtimeSnapshot.isAirplaneModeEnabled,
                    targetEnabled = true,
                    triggerSource = TriggerSource.AppForegroundChanged,
                    reason = action.reason,
                    onPrompt = { showDisconnectPrompt(settings) },
                )
            }

            is ForegroundAction.ScheduleDisconnect -> {
                scheduleAppExitDisconnect(action.reason, action.delaySeconds)
            }

            is ForegroundAction.CancelScheduledDisconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = true)
            }

            is ForegroundAction.PauseAutomation -> {
                runtimeStatusRepository.updateSnapshot { snapshot ->
                    snapshot.copy(
                        lastAction = ExecutionAction.PauseAutomation,
                        lastTriggerSource = TriggerSource.AppForegroundChanged,
                        lastActionResult = ExecutionResult.Failed,
                        lastActionReason = action.reason,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private suspend fun scheduleScreenOffDisconnectIfNeeded() {
        val settings = settingsRepository.settings.first()
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        if (!automationRuleEngine.shouldScheduleScreenOffDisconnect(
                settings = settings,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
            )
        ) {
            cancelScreenOffDisconnect()
            return
        }
        if (runtimeSnapshot.isAirplaneModeEnabled == true) {
            cancelScreenOffDisconnect()
            return
        }
        if (screenOffDisconnectJob?.isActive == true) {
            return
        }
        val delayMillis = settings.screenOffDelaySeconds.coerceAtLeast(0) * 1_000L
        val executeAtMillis = System.currentTimeMillis() + delayMillis
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = true,
                pendingScreenOffDisconnectAtMillis = executeAtMillis,
                lastAction = ExecutionAction.ScheduleScreenOffDisconnect,
                lastTriggerSource = TriggerSource.ScreenOff,
                lastActionResult = ExecutionResult.Pending,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        screenOffDisconnectJob = serviceScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            val latestSettings = settingsRepository.settings.first()
            val latestRuntimeSnapshot = runtimeStatusRepository.snapshot.first()
            val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
            val shouldDisconnect = automationRuleEngine.shouldExecuteScreenOffDisconnect(
                settings = latestSettings,
                screenState = currentScreenState,
                isWifiConnected = latestRuntimeSnapshot.isWifiConnected,
                executorAvailable = latestExecutorAvailable,
            ) && latestRuntimeSnapshot.isAirplaneModeEnabled != true
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isScreenOffDisconnectScheduled = false,
                    pendingScreenOffDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            screenOffDisconnectJob = null
            if (!shouldDisconnect) {
                return@launch
            }
            executeAirplaneModeChange(
                currentEnabled = latestRuntimeSnapshot.isAirplaneModeEnabled,
                targetEnabled = true,
                triggerSource = TriggerSource.ScreenOff,
                reason = "息屏延迟 ${latestSettings.screenOffDelaySeconds} 秒后执行断网",
                onPrompt = { showDisconnectPrompt(latestSettings) },
            )
        }
    }

    private suspend fun scheduleAppExitDisconnect(reason: String, delaySeconds: Int) {
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        if (runtimeSnapshot.isAirplaneModeEnabled == true) {
            cancelAppExitDisconnect(updateRuntimeState = false)
            return
        }
        if (appExitDisconnectJob?.isActive == true) {
            return
        }
        val delayMillis = delaySeconds.coerceAtLeast(0) * 1_000L
        val executeAtMillis = System.currentTimeMillis() + delayMillis
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = true,
                pendingAppExitDisconnectAtMillis = executeAtMillis,
                lastAction = ExecutionAction.ScheduleAppExitDisconnect,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
                lastActionResult = ExecutionResult.Pending,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        appExitDisconnectJob = serviceScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            val latestSettings = settingsRepository.settings.first()
            val latestRuntimeSnapshot = runtimeStatusRepository.snapshot.first()
            val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
            val shouldDisconnect = latestSettings.automationEnabled &&
                latestSettings.appExitDisconnectEnabled &&
                lastTargetAppActive == false &&
                latestExecutorAvailable &&
                latestRuntimeSnapshot.isAirplaneModeEnabled != true &&
                !(latestRuntimeSnapshot.isWifiConnected && latestSettings.skipDisconnectOnWifi)
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isAppExitDisconnectScheduled = false,
                    pendingAppExitDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            appExitDisconnectJob = null
            if (!shouldDisconnect) {
                return@launch
            }
            executeAirplaneModeChange(
                currentEnabled = latestRuntimeSnapshot.isAirplaneModeEnabled,
                targetEnabled = true,
                triggerSource = TriggerSource.AppForegroundChanged,
                reason = reason,
                onPrompt = { showDisconnectPrompt(latestSettings) },
            )
        }
    }

    private suspend fun executeAirplaneModeChange(
        currentEnabled: Boolean?,
        targetEnabled: Boolean,
        triggerSource: TriggerSource,
        reason: String,
        onPrompt: suspend () -> Unit,
    ) {
        if (currentEnabled == targetEnabled) {
            return
        }
        accessRepository.setAirplaneModeState(
            enabled = targetEnabled,
            triggerSource = triggerSource,
            reason = reason,
        )
        val updatedSnapshot = runtimeStatusRepository.snapshot.first()
        if (updatedSnapshot.lastActionResult == ExecutionResult.Success) {
            onPrompt()
        }
    }

    private suspend fun showReconnectPrompt(settings: com.gaozay.smartflight.settings.UserSettings) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(settings.reconnectPromptText.ifBlank { "SmartFlight 已恢复联网" })
    }

    private suspend fun showDisconnectPrompt(settings: com.gaozay.smartflight.settings.UserSettings) {
        if (!settings.showDisconnectPrompt) {
            return
        }
        showPrompt(settings.disconnectPromptText.ifBlank { "SmartFlight 已断网" })
    }

    private suspend fun showPrompt(message: String) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun updateSkippedForegroundDecision(decision: ForegroundRuleDecision) {
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.DoNothing,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
                lastActionResult = ExecutionResult.Skipped,
                lastActionReason = decision.reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun cancelScreenOffDisconnect() {
        val job = screenOffDisconnectJob
        val wasScheduled = job?.isActive == true
        job?.cancel()
        screenOffDisconnectJob = null
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                lastAction = ExecutionAction.CancelScheduledDisconnect,
                lastTriggerSource = when (currentScreenState) {
                    ScreenState.Unlocked -> TriggerSource.UserUnlocked
                    ScreenState.ScreenOn -> TriggerSource.ScreenOn
                    else -> TriggerSource.Manual
                },
                lastActionResult = if (wasScheduled) ExecutionResult.Success else ExecutionResult.Skipped,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun cancelAppExitDisconnect(updateRuntimeState: Boolean) {
        val job = appExitDisconnectJob
        val wasScheduled = job?.isActive == true
        job?.cancel()
        appExitDisconnectJob = null
        if (!updateRuntimeState) {
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isAppExitDisconnectScheduled = false,
                    pendingAppExitDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                lastAction = ExecutionAction.CancelScheduledDisconnect,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
                lastActionResult = if (wasScheduled) ExecutionResult.Success else ExecutionResult.Skipped,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

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
        private const val NOTIFICATION_CHANNEL_ID = "automation_runtime"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.gaozay.smartflight.runtime.action.START"
        const val ACTION_DEBUG_SCREEN_OFF = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_OFF"
        const val ACTION_DEBUG_SCREEN_ON = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_ON"
    }
}

private data class AppRuntimeRuleInfo(
    val status: AppListStatus,
    val isCandidate: Boolean,
)
