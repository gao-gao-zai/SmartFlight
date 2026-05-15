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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.gaozay.smartflight.MainActivity
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.sourceTag
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.runtime.isDisconnected
import com.gaozay.smartflight.runtime.mobileDataNoOpSuffix
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

    @Inject
    lateinit var runtimeEnvironmentMonitor: RuntimeEnvironmentMonitor

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
            Log.d(LOG_TAG, "screen broadcast action=$action currentScreenState=$currentScreenState")
            currentScreenState = when (action) {
                Intent.ACTION_SCREEN_OFF -> ScreenState.ScreenOff
                Intent.ACTION_USER_PRESENT -> ScreenState.Unlocked
                Intent.ACTION_SCREEN_ON -> ScreenState.ScreenOn
                else -> currentScreenState
            }
            serviceScope.launch {
                when (action) {
                    Intent.ACTION_SCREEN_OFF -> scheduleScreenOffDisconnectIfNeeded()
                    Intent.ACTION_SCREEN_ON -> handleScreenWake(TriggerSource.ScreenOn)
                    Intent.ACTION_USER_PRESENT -> handleScreenWake(TriggerSource.UserUnlocked)
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
        Log.d(LOG_TAG, "service onCreate screenState=$currentScreenState")
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        runtimeEnvironmentMonitor.register(serviceScope)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch {
            accessRepository.refresh()
            accessRepository.syncCurrentNetworkControlState()
            runtimeEnvironmentMonitor.refreshSnapshot()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "service onStartCommand action=${intent?.action} startId=$startId screenState=$currentScreenState")
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
                    handleScreenWake(TriggerSource.ScreenOn)
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
        Log.d(LOG_TAG, "service onDestroy screenState=$currentScreenState lastTargetAppActive=$lastTargetAppActive")
        automationJob?.cancel()
        screenOffDisconnectJob?.cancel()
        appExitDisconnectJob?.cancel()
        runtimeEnvironmentMonitor.unregister()
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
            Log.d(LOG_TAG, "automation loop already active")
            return
        }
        Log.d(LOG_TAG, "starting automation loop")
        automationJob = serviceScope.launch {
            launch {
                installedAppRepository.observeApps().collect { apps ->
                    appRulesByPackageName = apps.associate { app ->
                    app.packageName to AppRuntimeRuleInfo(
                            isInOnlineList = app.isInOnlineList,
                            isInBlacklist = app.isInBlacklist,
                            sourceTag = app.sourceTag(),
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
                    Log.d(LOG_TAG, "automation disabled during loop, stopping service")
                    stopSelf()
                    return@launch
                }
                runCatching {
                    automationTick()
                }.onFailure { throwable ->
                    Log.e(LOG_TAG, "automationTick failed", throwable)
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

    private suspend fun automationTick(
        triggerSource: TriggerSource = TriggerSource.AppForegroundChanged,
        allowReconnectWhenTargetAppAlreadyActive: Boolean = false,
    ) {
        val settings = settingsRepository.settings.first()
        Log.d(
            LOG_TAG,
            "automationTick trigger=$triggerSource screenState=$currentScreenState allowWakeReconnect=$allowReconnectWhenTargetAppAlreadyActive monitorWhenScreenOff=${settings.monitorForegroundWhenScreenOff} reconnectOnLaunch=${settings.reconnectOnTargetAppLaunch}",
        )
        if (!automationRuleEngine.shouldMonitorForeground(settings, currentScreenState)) {
            Log.d(LOG_TAG, "automationTick skipped: shouldMonitorForeground=false screenState=$currentScreenState")
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
        Log.d(
            LOG_TAG,
            "automationTick snapshot pkg=${packageName ?: "<none>"} online=${appRuleInfo?.isInOnlineList == true} blacklist=${appRuleInfo?.isInBlacklist == true} executorAvailable=$executorAvailable disconnected=${runtimeSnapshot.isDisconnected(settings.networkControlMode)} wifi=${runtimeSnapshot.isWifiConnected} lastTarget=$lastTargetAppActive",
        )
        val decision = automationRuleEngine.evaluateForegroundChange(
            ForegroundRuleContext(
                settings = settings,
                packageName = packageName,
                appLabel = foregroundApp?.appLabel,
                isInOnlineList = appRuleInfo?.isInOnlineList == true,
                isInBlacklist = appRuleInfo?.isInBlacklist == true,
                onlineSource = appRuleInfo?.sourceTag,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
                previousTargetAppActive = lastTargetAppActive,
                isCurrentlyDisconnected = runtimeSnapshot.isDisconnected(settings.networkControlMode),
                isAppExitDisconnectScheduled = runtimeSnapshot.isAppExitDisconnectScheduled,
                allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
            ),
        )
        Log.d(
            LOG_TAG,
            buildString {
                append("foreground decision")
                append(" pkg=")
                append(packageName ?: "<none>")
                append(" label=")
                append(foregroundApp?.appLabel ?: "<none>")
                append(" prevTarget=")
                append(lastTargetAppActive)
                append(" target=")
                append(decision.targetAppActive)
                append(" disconnected=")
                append(runtimeSnapshot.isDisconnected(settings.networkControlMode))
                append(" appExitScheduled=")
                append(runtimeSnapshot.isAppExitDisconnectScheduled)
                append(" screenOffScheduled=")
                append(runtimeSnapshot.isScreenOffDisconnectScheduled)
                append(" delay=")
                append(settings.appExitDelaySeconds)
                append("s")
                append(" action=")
                append(decision.action::class.simpleName)
                append(" rules=")
                append(decision.matchedRules.joinToString(","))
                append(" reason=")
                append(decision.reason)
            },
        )
        lastTargetAppActive = decision.targetAppActive

        when (val action = decision.action) {
            is ForegroundAction.None -> {
                if (decision.shouldLog) {
                    updateSkippedForegroundDecision(decision, triggerSource)
                }
            }
            is ForegroundAction.Reconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = true)
                executeAirplaneModeChange(
                    currentDisconnected = runtimeSnapshot.isDisconnected(settings.networkControlMode),
                    targetDisconnected = false,
                    triggerSource = triggerSource,
                    reason = action.reason + settings.mobileDataNoOpSuffix(),
                    onPrompt = { showReconnectPrompt(settings) },
                )
            }

            is ForegroundAction.Disconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = false)
                executeAirplaneModeChange(
                    currentDisconnected = runtimeSnapshot.isDisconnected(settings.networkControlMode),
                    targetDisconnected = true,
                    triggerSource = triggerSource,
                    reason = action.reason + settings.mobileDataNoOpSuffix(),
                    onPrompt = { showDisconnectPrompt(settings) },
                )
            }

            is ForegroundAction.ScheduleDisconnect -> {
                val eventTimestampMillis = foregroundApp?.eventTimestampMillis ?: System.currentTimeMillis()
                scheduleAppExitDisconnect(
                    reason = action.reason,
                    delaySeconds = action.delaySeconds,
                    baseTimestampMillis = eventTimestampMillis,
                )
            }

            is ForegroundAction.CancelScheduledDisconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = true)
            }

            is ForegroundAction.PauseAutomation -> {
                runtimeStatusRepository.updateSnapshot { snapshot ->
                    snapshot.copy(
                        lastAction = ExecutionAction.PauseAutomation,
                        lastTriggerSource = triggerSource,
                        lastActionResult = ExecutionResult.Failed,
                        lastActionReason = action.reason,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private suspend fun handleScreenWake(triggerSource: TriggerSource) {
        Log.d(LOG_TAG, "handleScreenWake trigger=$triggerSource screenState=$currentScreenState lastTarget=$lastTargetAppActive")
        cancelScreenOffDisconnect()
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        val settings = settingsRepository.settings.first()
        val allowReconnectWhenTargetAppAlreadyActive = when (triggerSource) {
            TriggerSource.ScreenOn -> !settings.disableScreenOnReconnect
            TriggerSource.UserUnlocked -> !settings.disableUnlockReconnect
            else -> false
        }
        Log.d(
            LOG_TAG,
            "handleScreenWake prepared trigger=$triggerSource allowReconnect=$allowReconnectWhenTargetAppAlreadyActive disableScreenOnReconnect=${settings.disableScreenOnReconnect} disableUnlockReconnect=${settings.disableUnlockReconnect}",
        )
        automationTick(
            triggerSource = triggerSource,
            allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
        )
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
            Log.d(LOG_TAG, "skip screen-off schedule: conditions not met")
            cancelScreenOffDisconnect()
            return
        }
        if (runtimeSnapshot.isDisconnected(settings.networkControlMode) == true) {
            Log.d(LOG_TAG, "skip screen-off schedule: already disconnected")
            cancelScreenOffDisconnect()
            return
        }
        if (screenOffDisconnectJob?.isActive == true) {
            Log.d(LOG_TAG, "skip screen-off schedule: job already active")
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
        Log.d(
            LOG_TAG,
            "scheduled screen-off disconnect delay=${settings.screenOffDelaySeconds}s executeAt=$executeAtMillis now=${System.currentTimeMillis()}",
        )
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
            ) && latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode) != true
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isScreenOffDisconnectScheduled = false,
                    pendingScreenOffDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            screenOffDisconnectJob = null
            if (!shouldDisconnect) {
                Log.d(LOG_TAG, "skip screen-off execution after delay: conditions changed")
                return@launch
            }
            Log.d(LOG_TAG, "execute screen-off disconnect after delay")
            executeAirplaneModeChange(
                currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
                targetDisconnected = true,
                triggerSource = TriggerSource.ScreenOff,
                reason = "息屏延迟 ${latestSettings.screenOffDelaySeconds} 秒后执行断网${latestSettings.mobileDataNoOpSuffix()}",
                onPrompt = { showDisconnectPrompt(latestSettings) },
            )
        }
    }

    private suspend fun scheduleAppExitDisconnect(
        reason: String,
        delaySeconds: Int,
        baseTimestampMillis: Long = System.currentTimeMillis(),
    ) {
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val currentSettings = settingsRepository.settings.first()
        if (runtimeSnapshot.isDisconnected(currentSettings.networkControlMode) == true) {
            Log.d(LOG_TAG, "skip app-exit schedule: already disconnected")
            cancelAppExitDisconnect(updateRuntimeState = false)
            return
        }
        if (appExitDisconnectJob?.isActive == true) {
            Log.d(LOG_TAG, "skip app-exit schedule: job already active")
            return
        }
        val totalDelayMillis = delaySeconds.coerceAtLeast(0) * 1_000L
        val now = System.currentTimeMillis()
        val effectiveBaseMillis = baseTimestampMillis.coerceAtMost(now)
        val elapsedMillis = (now - effectiveBaseMillis).coerceAtLeast(0L)
        val delayMillis = (totalDelayMillis - elapsedMillis).coerceAtLeast(0L)
        val executeAtMillis = now + delayMillis
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
        Log.d(
            LOG_TAG,
            "scheduled app-exit disconnect delay=${delaySeconds}s base=$baseTimestampMillis now=$now remainingMs=$delayMillis executeAt=$executeAtMillis reason=$reason",
        )
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
                latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode) != true &&
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
                Log.d(
                    LOG_TAG,
                    "skip app-exit execution after delay: targetActive=$lastTargetAppActive disconnected=${latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode)} wifi=${latestRuntimeSnapshot.isWifiConnected}",
                )
                return@launch
            }
            Log.d(LOG_TAG, "execute app-exit disconnect after delay reason=$reason")
            executeAirplaneModeChange(
                currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
                targetDisconnected = true,
                triggerSource = TriggerSource.AppForegroundChanged,
                reason = reason + latestSettings.mobileDataNoOpSuffix(),
                onPrompt = { showDisconnectPrompt(latestSettings) },
            )
        }
    }

    private suspend fun executeAirplaneModeChange(
        currentDisconnected: Boolean?,
        targetDisconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String,
        onPrompt: suspend () -> Unit,
    ) {
        if (currentDisconnected == targetDisconnected) {
            Log.d(
                LOG_TAG,
                "skip network change: already target state trigger=$triggerSource current=$currentDisconnected target=$targetDisconnected reason=$reason",
            )
            return
        }
        Log.d(
            LOG_TAG,
            "execute network change trigger=$triggerSource current=$currentDisconnected target=$targetDisconnected reason=$reason",
        )
        accessRepository.setDisconnectedState(
            disconnected = targetDisconnected,
            triggerSource = triggerSource,
            reason = reason,
        )
        val updatedSnapshot = runtimeStatusRepository.snapshot.first()
        Log.d(
            LOG_TAG,
            "network change result trigger=$triggerSource result=${updatedSnapshot.lastActionResult} action=${updatedSnapshot.lastAction} reason=${updatedSnapshot.lastActionReason}",
        )
        if (updatedSnapshot.lastActionResult == ExecutionResult.Success) {
            if (targetDisconnected) {
                clearPendingDisconnects()
            }
            onPrompt()
        }
    }

    private suspend fun clearPendingDisconnects() {
        screenOffDisconnectJob?.cancel()
        screenOffDisconnectJob = null
        appExitDisconnectJob?.cancel()
        appExitDisconnectJob = null
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        Log.d(LOG_TAG, "cleared pending disconnect jobs")
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

    private suspend fun updateSkippedForegroundDecision(
        decision: ForegroundRuleDecision,
        triggerSource: TriggerSource,
    ) {
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.DoNothing,
                lastTriggerSource = triggerSource,
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
        Log.d(LOG_TAG, "cancel screen-off disconnect scheduled=$wasScheduled")
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
            Log.d(LOG_TAG, "cancel app-exit disconnect without runtime update scheduled=$wasScheduled")
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
        Log.d(LOG_TAG, "cancel app-exit disconnect scheduled=$wasScheduled")
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
        private const val LOG_TAG = "SmartFlightRuntime"
        private const val NOTIFICATION_CHANNEL_ID = "automation_runtime"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.gaozay.smartflight.runtime.action.START"
        const val ACTION_DEBUG_SCREEN_OFF = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_OFF"
        const val ACTION_DEBUG_SCREEN_ON = "com.gaozay.smartflight.runtime.action.DEBUG_SCREEN_ON"
    }
}

private data class AppRuntimeRuleInfo(
    val isInOnlineList: Boolean,
    val isInBlacklist: Boolean,
    val sourceTag: AppOnlineSourceTag?,
)
