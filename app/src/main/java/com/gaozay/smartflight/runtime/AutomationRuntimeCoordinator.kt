package com.gaozay.smartflight.runtime

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.sourceTag
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import com.gaozay.smartflight.settings.shouldClearExpiredTemporaryDisable
import com.gaozay.smartflight.settings.withTemporaryDisableCleared
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AutomationRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val accessRepository: AccessRepository,
    private val foregroundAppDetector: ForegroundAppDetector,
    private val automationRuleEngine: AutomationRuleEngine,
    private val runtimeEnvironmentMonitor: RuntimeEnvironmentMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<RuntimeEvent>(Channel.UNLIMITED)

    private var eventLoopJob: Job? = null
    private var settingsJob: Job? = null
    private var appsJob: Job? = null
    private var foregroundProbeJob: Job? = null
    private var screenOffDisconnectJob: Job? = null
    private var appExitDisconnectJob: Job? = null
    private var temporaryDisableExpiryJob: Job? = null
    private var state = RuntimeState()

    fun start(initialScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            eventLoopJob = scope.launch {
                for (event in events) {
                    runCatching {
                        handleEvent(event)
                    }.onFailure { throwable ->
                        Log.e(LOG_TAG, "runtime event failed event=${event.nameForLog()}", throwable)
                        runtimeStatusRepository.updateSnapshot { snapshot ->
                            snapshot.copy(
                                lastTriggerSource = TriggerSource.ServiceRestored,
                                lastActionResult = ExecutionResult.Failed,
                                lastActionReason = "自动化事件处理失败：${throwable.message ?: "未知错误"}",
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }
        }
        send(RuntimeEvent.Started(initialScreenState))
    }

    fun onScreenOff() {
        send(RuntimeEvent.ScreenOff)
    }

    fun onScreenOn() {
        send(RuntimeEvent.ScreenOn)
    }

    fun onUserUnlocked() {
        send(RuntimeEvent.UserUnlocked)
    }

    fun onNetworkChanged() {
        send(RuntimeEvent.NetworkChanged)
    }

    suspend fun stop(finalScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            return
        }
        val ack = CompletableDeferred<Unit>()
        send(RuntimeEvent.Stopped(finalScreenState, ack))
        ack.await()
        events.close()
        scope.cancel()
    }

    private fun send(event: RuntimeEvent) {
        if (events.trySend(event).isFailure) {
            Log.w(LOG_TAG, "runtime event dropped event=${event.nameForLog()}")
        }
    }

    private suspend fun handleEvent(event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.Started -> handleStarted(event.initialScreenState)
            is RuntimeEvent.Stopped -> handleStopped(event.finalScreenState, event.ack)
            RuntimeEvent.ScreenOff -> handleScreenOff()
            RuntimeEvent.ScreenOn -> handleScreenWake(
                screenState = ScreenState.ScreenOn,
                triggerSource = TriggerSource.ScreenOn,
            )
            RuntimeEvent.UserUnlocked -> handleScreenWake(
                screenState = ScreenState.Unlocked,
                triggerSource = TriggerSource.UserUnlocked,
            )
            RuntimeEvent.ForegroundProbeTick -> handleForegroundProbeTick()
            is RuntimeEvent.SettingsChanged -> handleSettingsChanged(event.settings)
            is RuntimeEvent.AppsChanged -> {
                state = state.copy(appRulesByPackageName = event.appRulesByPackageName)
            }
            RuntimeEvent.NetworkChanged -> runtimeEnvironmentMonitor.refreshSnapshot()
            RuntimeEvent.TemporaryDisableExpired -> handleTemporaryDisableExpired()
            RuntimeEvent.ScreenOffDisconnectDue -> handleScreenOffDisconnectDue()
            RuntimeEvent.AppExitDisconnectDue -> handleAppExitDisconnectDue()
        }
    }

    private suspend fun handleStarted(initialScreenState: ScreenState) {
        val settings = settingsRepository.settings.first()
        val appRules = installedAppRepository.observeApps().first().associate { app ->
            app.packageName to AppRuntimeRuleInfo(
                isInOnlineList = app.isInOnlineList,
                isInBlacklist = app.isInBlacklist,
                sourceTag = app.sourceTag(),
            )
        }
        state = state.copy(
            settings = settings,
            screenState = initialScreenState,
            appRulesByPackageName = appRules,
        )
        startCollectors()
        runtimeEnvironmentMonitor.register(scope)
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isForegroundServiceRunning = true,
                screenState = initialScreenState,
                lastTriggerSource = TriggerSource.ServiceRestored,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        scheduleForegroundProbe(immediate = true)
    }

    private fun startCollectors() {
        if (settingsJob?.isActive != true) {
            settingsJob = scope.launch {
                settingsRepository.settings.collect { settings ->
                    send(RuntimeEvent.SettingsChanged(settings))
                }
            }
        }
        if (appsJob?.isActive != true) {
            appsJob = scope.launch {
                installedAppRepository.observeApps().collect { apps ->
                    send(
                        RuntimeEvent.AppsChanged(
                            apps.associate { app ->
                                app.packageName to AppRuntimeRuleInfo(
                                    isInOnlineList = app.isInOnlineList,
                                    isInBlacklist = app.isInBlacklist,
                                    sourceTag = app.sourceTag(),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handleStopped(
        finalScreenState: ScreenState,
        ack: CompletableDeferred<Unit>,
    ) {
        cancelForegroundProbe()
        cancelScreenOffDisconnect(updateRuntimeState = false)
        cancelAppExitDisconnect(updateRuntimeState = false)
        cancelTemporaryDisableExpiry()
        settingsJob?.cancel()
        appsJob?.cancel()
        settingsJob = null
        appsJob = null
        runtimeEnvironmentMonitor.unregister()
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isForegroundServiceRunning = false,
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                isAppExitDisconnectScheduled = false,
                pendingAppExitDisconnectAtMillis = null,
                screenState = finalScreenState,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        ack.complete(Unit)
    }

    private suspend fun handleSettingsChanged(settings: UserSettings) {
        val previous = state.settings
        state = state.copy(settings = settings)
        if (!settings.automationEnabled) {
            cancelForegroundProbe()
            cancelScreenOffDisconnect(updateRuntimeState = true)
            cancelAppExitDisconnect(updateRuntimeState = true)
            cancelTemporaryDisableExpiry()
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isForegroundServiceRunning = true,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }

        if (settings.isTemporaryDisableActive()) {
            cancelScreenOffDisconnect(updateRuntimeState = true)
            cancelAppExitDisconnect(updateRuntimeState = true)
            scheduleTemporaryDisableExpiry(settings)
        } else {
            cancelTemporaryDisableExpiry()
        }

        if (previous.monitorForegroundWhenScreenOff != settings.monitorForegroundWhenScreenOff ||
            previous.automationEnabled != settings.automationEnabled ||
            previous.temporaryDisableMode != settings.temporaryDisableMode
        ) {
            scheduleForegroundProbe(immediate = true)
        }
    }

    private suspend fun handleScreenOff() {
        state = state.copy(screenState = ScreenState.ScreenOff)
        cancelForegroundProbe()
        if (state.settings.temporaryDisableMode == AutomationDisableMode.UntilScreenOff &&
            state.settings.isTemporaryDisableActive()
        ) {
            clearTemporaryDisable("已息屏，恢复自动化")
        }
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                screenState = ScreenState.ScreenOff,
                isForegroundServiceRunning = true,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        scheduleScreenOffDisconnectIfNeeded()
        scheduleForegroundProbe(immediate = false)
    }

    private suspend fun handleScreenWake(
        screenState: ScreenState,
        triggerSource: TriggerSource,
    ) {
        state = state.copy(screenState = screenState)
        cancelScreenOffDisconnect(updateRuntimeState = true)
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        val settings = state.settings
        if (settings.shouldClearExpiredTemporaryDisable()) {
            clearTemporaryDisable("临时禁用已到期，恢复自动化")
        }
        if (state.settings.isTemporaryDisableActive()) {
            scheduleForegroundProbe(immediate = false)
            return
        }
        val allowReconnectWhenTargetAppAlreadyActive = when (triggerSource) {
            TriggerSource.ScreenOn -> !state.settings.disableScreenOnReconnect
            TriggerSource.UserUnlocked -> !state.settings.disableUnlockReconnect
            else -> false
        }
        Log.d(
            LOG_TAG,
            "handleScreenWake trigger=$triggerSource screenState=$screenState allowReconnect=$allowReconnectWhenTargetAppAlreadyActive",
        )
        automationTick(
            triggerSource = triggerSource,
            allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
        )
        scheduleForegroundProbe(immediate = false)
    }

    private suspend fun handleForegroundProbeTick() {
        foregroundProbeJob = null
        if (!state.settings.automationEnabled) {
            return
        }
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            clearTemporaryDisable("临时禁用已到期，恢复自动化")
        }
        automationTick()
        scheduleForegroundProbe(immediate = false)
    }

    private suspend fun handleTemporaryDisableExpired() {
        temporaryDisableExpiryJob = null
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            clearTemporaryDisable("临时禁用已到期，恢复自动化")
            scheduleScreenOffDisconnectIfNeeded()
            scheduleForegroundProbe(immediate = true)
        }
    }

    private fun scheduleForegroundProbe(immediate: Boolean) {
        cancelForegroundProbe()
        val settings = state.settings
        if (!settings.automationEnabled) {
            return
        }
        if (!automationRuleEngine.shouldMonitorForeground(settings, state.screenState)) {
            return
        }
        val delayMillis = if (immediate) 0L else foregroundProbeIntervalMillis(state.screenState)
        foregroundProbeJob = scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            send(RuntimeEvent.ForegroundProbeTick)
        }
    }

    private fun cancelForegroundProbe() {
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
    }

    private fun scheduleTemporaryDisableExpiry(settings: UserSettings) {
        val untilMillis = settings.temporaryDisableUntilMillis ?: return
        temporaryDisableExpiryJob?.cancel()
        temporaryDisableExpiryJob = scope.launch {
            delay((untilMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            send(RuntimeEvent.TemporaryDisableExpired)
        }
    }

    private fun cancelTemporaryDisableExpiry() {
        temporaryDisableExpiryJob?.cancel()
        temporaryDisableExpiryJob = null
    }

    private suspend fun automationTick(
        triggerSource: TriggerSource = TriggerSource.AppForegroundChanged,
        allowReconnectWhenTargetAppAlreadyActive: Boolean = false,
    ) {
        val settings = state.settings
        Log.d(
            LOG_TAG,
            "automationTick trigger=$triggerSource screenState=${state.screenState} allowWakeReconnect=$allowReconnectWhenTargetAppAlreadyActive monitorWhenScreenOff=${settings.monitorForegroundWhenScreenOff} reconnectOnLaunch=${settings.reconnectOnTargetAppLaunch}",
        )
        if (!automationRuleEngine.shouldMonitorForeground(settings, state.screenState)) {
            Log.d(LOG_TAG, "automationTick skipped: shouldMonitorForeground=false screenState=${state.screenState}")
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    screenState = state.screenState,
                    isForegroundServiceRunning = true,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }
        val foregroundApp = foregroundAppDetector.detect()
        state = state.copy(lastKnownForegroundApp = foregroundApp ?: state.lastKnownForegroundApp)
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                currentForegroundPackageName = foregroundApp?.packageName,
                currentForegroundAppLabel = foregroundApp?.appLabel,
                screenState = state.screenState,
                isForegroundServiceRunning = true,
                updatedAtMillis = if (foregroundApp != null) System.currentTimeMillis() else snapshot.updatedAtMillis,
            )
        }

        val packageName = foregroundApp?.packageName
        if (settings.isTemporaryDisableActive()) {
            if (settings.temporaryDisableMode == AutomationDisableMode.UntilAppSwitch &&
                packageName != null &&
                settings.temporaryDisableForegroundPackageName != null &&
                packageName != settings.temporaryDisableForegroundPackageName
            ) {
                clearTemporaryDisable("检测到应用切换，恢复自动化")
            } else {
                runtimeStatusRepository.updateSnapshot { snapshot ->
                    snapshot.copy(
                        lastAction = ExecutionAction.PauseAutomation,
                        lastTriggerSource = triggerSource,
                        lastActionResult = ExecutionResult.Pending,
                        lastActionReason = settings.temporaryDisableMode.label,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
                return
            }
        }
        val effectiveSettings = state.settings
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val appRuleInfo = packageName?.let { state.appRulesByPackageName[it] }
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val isDisconnected = runtimeSnapshot.isDisconnected(effectiveSettings.networkControlMode)
        Log.d(
            LOG_TAG,
            "automationTick snapshot pkg=${packageName ?: "<none>"} online=${appRuleInfo?.isInOnlineList == true} blacklist=${appRuleInfo?.isInBlacklist == true} executorAvailable=$executorAvailable disconnected=$isDisconnected wifi=${runtimeSnapshot.isWifiConnected} lastTarget=${state.lastTargetAppActive}",
        )
        val decision = automationRuleEngine.evaluateForegroundChange(
            ForegroundRuleContext(
                settings = effectiveSettings,
                packageName = packageName,
                appLabel = foregroundApp?.appLabel,
                isInOnlineList = appRuleInfo?.isInOnlineList == true,
                isInBlacklist = appRuleInfo?.isInBlacklist == true,
                onlineSource = appRuleInfo?.sourceTag,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
                previousTargetAppActive = state.lastTargetAppActive,
                isCurrentlyDisconnected = isDisconnected,
                isAppExitDisconnectScheduled = appExitDisconnectJob?.isActive == true,
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
                append(state.lastTargetAppActive)
                append(" target=")
                append(decision.targetAppActive)
                append(" disconnected=")
                append(isDisconnected)
                append(" appExitScheduled=")
                append(appExitDisconnectJob?.isActive == true)
                append(" screenOffScheduled=")
                append(screenOffDisconnectJob?.isActive == true)
                append(" delay=")
                append(effectiveSettings.appExitDelaySeconds)
                append("s action=")
                append(decision.action::class.simpleName)
                append(" rules=")
                append(decision.matchedRules.joinToString(","))
                append(" reason=")
                append(decision.reason)
            },
        )
        state = state.copy(lastTargetAppActive = decision.targetAppActive)

        when (val action = decision.action) {
            is ForegroundAction.None -> {
                if (decision.shouldLog) {
                    updateSkippedForegroundDecision(decision, triggerSource)
                }
            }
            is ForegroundAction.Reconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = true)
                executeNetworkChange(
                    currentDisconnected = isDisconnected,
                    targetDisconnected = false,
                    triggerSource = triggerSource,
                    reason = action.reason + effectiveSettings.mobileDataNoOpSuffix(),
                    onPrompt = { showReconnectPrompt(effectiveSettings) },
                )
            }
            is ForegroundAction.Disconnect -> {
                cancelAppExitDisconnect(updateRuntimeState = false)
                executeNetworkChange(
                    currentDisconnected = isDisconnected,
                    targetDisconnected = true,
                    triggerSource = triggerSource,
                    reason = action.reason + effectiveSettings.mobileDataNoOpSuffix(),
                    onPrompt = { showDisconnectPrompt(effectiveSettings) },
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

    private suspend fun scheduleScreenOffDisconnectIfNeeded() {
        val settings = state.settings
        if (!settings.isAutomationEffectivelyEnabled()) {
            Log.d(LOG_TAG, "skip screen-off schedule: automation temporarily disabled")
            cancelScreenOffDisconnect(updateRuntimeState = true)
            return
        }
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val executorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        if (!automationRuleEngine.shouldScheduleScreenOffDisconnect(
                settings = settings,
                isWifiConnected = runtimeSnapshot.isWifiConnected,
                executorAvailable = executorAvailable,
            )
        ) {
            Log.d(LOG_TAG, "skip screen-off schedule: conditions not met")
            cancelScreenOffDisconnect(updateRuntimeState = true)
            return
        }
        if (runtimeSnapshot.isDisconnected(settings.networkControlMode) == true) {
            Log.d(LOG_TAG, "skip screen-off schedule: already disconnected")
            cancelScreenOffDisconnect(updateRuntimeState = true)
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
        screenOffDisconnectJob = scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            send(RuntimeEvent.ScreenOffDisconnectDue)
        }
    }

    private suspend fun handleScreenOffDisconnectDue() {
        screenOffDisconnectJob = null
        val latestSettings = state.settings
        if (!latestSettings.isAutomationEffectivelyEnabled()) {
            Log.d(LOG_TAG, "skip screen-off execution after delay: automation temporarily disabled")
            runtimeStatusRepository.updateSnapshot { snapshot ->
                snapshot.copy(
                    isScreenOffDisconnectScheduled = false,
                    pendingScreenOffDisconnectAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            return
        }
        val latestRuntimeSnapshot = runtimeStatusRepository.snapshot.first()
        val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val shouldDisconnect = automationRuleEngine.shouldExecuteScreenOffDisconnect(
            settings = latestSettings,
            screenState = state.screenState,
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
        if (!shouldDisconnect) {
            Log.d(LOG_TAG, "skip screen-off execution after delay: conditions changed")
            return
        }
        Log.d(LOG_TAG, "execute screen-off disconnect after delay")
        executeNetworkChange(
            currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
            targetDisconnected = true,
            triggerSource = TriggerSource.ScreenOff,
            reason = "息屏延迟 ${latestSettings.screenOffDelaySeconds} 秒后执行断网${latestSettings.mobileDataNoOpSuffix()}",
            onPrompt = { showDisconnectPrompt(latestSettings) },
        )
    }

    private suspend fun scheduleAppExitDisconnect(
        reason: String,
        delaySeconds: Int,
        baseTimestampMillis: Long = System.currentTimeMillis(),
    ) {
        val runtimeSnapshot = runtimeStatusRepository.snapshot.first()
        val currentSettings = state.settings
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
        appExitDisconnectJob = scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            send(RuntimeEvent.AppExitDisconnectDue)
        }
    }

    private suspend fun handleAppExitDisconnectDue() {
        appExitDisconnectJob = null
        val latestSettings = state.settings
        val latestRuntimeSnapshot = runtimeStatusRepository.snapshot.first()
        val latestExecutorAvailable = accessRepository.accessGateState.value.advancedAccess.isAvailable
        val shouldDisconnect = latestSettings.isAutomationEffectivelyEnabled() &&
            latestSettings.appExitDisconnectEnabled &&
            state.lastTargetAppActive == false &&
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
        if (!shouldDisconnect) {
            Log.d(
                LOG_TAG,
                "skip app-exit execution after delay: targetActive=${state.lastTargetAppActive} disconnected=${latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode)} wifi=${latestRuntimeSnapshot.isWifiConnected}",
            )
            return
        }
        Log.d(LOG_TAG, "execute app-exit disconnect after delay")
        executeNetworkChange(
            currentDisconnected = latestRuntimeSnapshot.isDisconnected(latestSettings.networkControlMode),
            targetDisconnected = true,
            triggerSource = TriggerSource.AppForegroundChanged,
            reason = "联网应用已离开前台${latestSettings.mobileDataNoOpSuffix()}",
            onPrompt = { showDisconnectPrompt(latestSettings) },
        )
    }

    private suspend fun executeNetworkChange(
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

    private suspend fun clearTemporaryDisable(reason: String) {
        val restoredSettings = state.settings.withTemporaryDisableCleared()
        state = state.copy(settings = restoredSettings)
        cancelTemporaryDisableExpiry()
        settingsRepository.updateSettings { it.withTemporaryDisableCleared() }
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                lastAction = ExecutionAction.PauseAutomation,
                lastTriggerSource = TriggerSource.SettingsChanged,
                lastActionResult = ExecutionResult.Success,
                lastActionReason = reason,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        Log.d(LOG_TAG, "temporary disable cleared: $reason")
    }

    private suspend fun cancelScreenOffDisconnect(updateRuntimeState: Boolean) {
        val job = screenOffDisconnectJob
        val wasScheduled = job?.isActive == true
        job?.cancel()
        screenOffDisconnectJob = null
        if (!updateRuntimeState && !wasScheduled) {
            return
        }
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isScreenOffDisconnectScheduled = false,
                pendingScreenOffDisconnectAtMillis = null,
                lastAction = if (updateRuntimeState) ExecutionAction.CancelScheduledDisconnect else snapshot.lastAction,
                lastTriggerSource = if (updateRuntimeState) {
                    when (state.screenState) {
                        ScreenState.Unlocked -> TriggerSource.UserUnlocked
                        ScreenState.ScreenOn -> TriggerSource.ScreenOn
                        else -> TriggerSource.Manual
                    }
                } else {
                    snapshot.lastTriggerSource
                },
                lastActionResult = if (updateRuntimeState) {
                    if (wasScheduled) ExecutionResult.Success else ExecutionResult.Skipped
                } else {
                    snapshot.lastActionResult
                },
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

    private suspend fun showReconnectPrompt(settings: UserSettings) {
        if (!settings.showReconnectPrompt) {
            return
        }
        showPrompt(settings.reconnectPromptText.ifBlank { "SmartFlight 已恢复联网" })
    }

    private suspend fun showDisconnectPrompt(settings: UserSettings) {
        if (!settings.showDisconnectPrompt) {
            return
        }
        showPrompt(settings.disconnectPromptText.ifBlank { "SmartFlight 已断网" })
    }

    private suspend fun showPrompt(message: String) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LOG_TAG = "SmartFlightRuntime"
        const val SCREEN_ON_POLL_INTERVAL_MILLIS = 1_500L
        const val SCREEN_OFF_POLL_INTERVAL_MILLIS = 8_000L

        fun foregroundProbeIntervalMillis(screenState: ScreenState): Long =
            when (screenState) {
                ScreenState.ScreenOff -> SCREEN_OFF_POLL_INTERVAL_MILLIS
                ScreenState.ScreenOn, ScreenState.Unlocked, ScreenState.Unknown -> SCREEN_ON_POLL_INTERVAL_MILLIS
            }
    }
}

internal sealed interface RuntimeEvent {
    data class Started(val initialScreenState: ScreenState) : RuntimeEvent
    data class Stopped(
        val finalScreenState: ScreenState,
        val ack: CompletableDeferred<Unit>,
    ) : RuntimeEvent
    data object ScreenOff : RuntimeEvent
    data object ScreenOn : RuntimeEvent
    data object UserUnlocked : RuntimeEvent
    data object ForegroundProbeTick : RuntimeEvent
    data class SettingsChanged(val settings: UserSettings) : RuntimeEvent
    data class AppsChanged(
        val appRulesByPackageName: Map<String, AppRuntimeRuleInfo>,
    ) : RuntimeEvent
    data object NetworkChanged : RuntimeEvent
    data object TemporaryDisableExpired : RuntimeEvent
    data object ScreenOffDisconnectDue : RuntimeEvent
    data object AppExitDisconnectDue : RuntimeEvent
}

private fun RuntimeEvent.nameForLog(): String = when (this) {
    is RuntimeEvent.Started -> "Started"
    is RuntimeEvent.Stopped -> "Stopped"
    RuntimeEvent.ScreenOff -> "ScreenOff"
    RuntimeEvent.ScreenOn -> "ScreenOn"
    RuntimeEvent.UserUnlocked -> "UserUnlocked"
    RuntimeEvent.ForegroundProbeTick -> "ForegroundProbeTick"
    is RuntimeEvent.SettingsChanged -> "SettingsChanged"
    is RuntimeEvent.AppsChanged -> "AppsChanged"
    RuntimeEvent.NetworkChanged -> "NetworkChanged"
    RuntimeEvent.TemporaryDisableExpired -> "TemporaryDisableExpired"
    RuntimeEvent.ScreenOffDisconnectDue -> "ScreenOffDisconnectDue"
    RuntimeEvent.AppExitDisconnectDue -> "AppExitDisconnectDue"
}

internal data class RuntimeState(
    val settings: UserSettings = UserSettings(),
    val screenState: ScreenState = ScreenState.Unknown,
    val lastTargetAppActive: Boolean? = null,
    val appRulesByPackageName: Map<String, AppRuntimeRuleInfo> = emptyMap(),
    val lastKnownForegroundApp: ForegroundAppInfo? = null,
)

internal data class AppRuntimeRuleInfo(
    val isInOnlineList: Boolean,
    val isInBlacklist: Boolean,
    val sourceTag: AppOnlineSourceTag?,
)
