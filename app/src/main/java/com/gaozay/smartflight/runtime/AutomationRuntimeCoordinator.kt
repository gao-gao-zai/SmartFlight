package com.gaozay.smartflight.runtime

import android.util.Log
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import com.gaozay.smartflight.settings.shouldClearExpiredTemporaryDisable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class AutomationRuntimeCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val accessRepository: AccessRepository,
    private val automationRuleEngine: AutomationRuleEngine,
    private val runtimeEnvironmentMonitor: RuntimeEnvironmentMonitor,
    private val reporter: RuntimeSnapshotReporter,
    private val foregroundAutomationHandler: ForegroundAutomationHandler,
    private val disconnectAutomationHandler: DisconnectAutomationHandler,
    private val temporaryDisableHandler: TemporaryDisableHandler,
    private val unexpectedNetworkChangeGuard: UnexpectedNetworkChangeGuard,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<RuntimeEvent>(Channel.UNLIMITED)
    private val scheduler = RuntimeTaskScheduler(
        scope = scope,
        send = ::send,
        reporter = reporter,
    )

    private var eventLoopJob: Job? = null
    private var settingsJob: Job? = null
    private var appsJob: Job? = null
    private var state = RuntimeState()

    fun start(initialScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            eventLoopJob = scope.launch {
                for (event in events) {
                    runCatching {
                        handleEvent(event)
                    }.onFailure { throwable ->
                        Log.e(LOG_TAG, "runtime event failed event=${event.nameForLog()}", throwable)
                        reporter.reportEventFailure(throwable)
                    }
                    if (event is RuntimeEvent.Stopped) {
                        events.close()
                        scope.cancel()
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

    fun requestStop(finalScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            return
        }
        send(RuntimeEvent.Stopped(finalScreenState, ack = null))
    }

    suspend fun stop(finalScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            return
        }
        val ack = CompletableDeferred<Unit>()
        send(RuntimeEvent.Stopped(finalScreenState, ack))
        ack.await()
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
            RuntimeEvent.NetworkChanged -> handleNetworkChanged()
            RuntimeEvent.TemporaryDisableExpired -> handleTemporaryDisableExpired()
            RuntimeEvent.ScreenOffDisconnectDue -> {
                disconnectAutomationHandler.handleScreenOffDisconnectDue(state, scheduler)
            }
            RuntimeEvent.AppExitDisconnectDue -> {
                disconnectAutomationHandler.handleAppExitDisconnectDue(state, scheduler)
            }
        }
    }

    private suspend fun handleStarted(initialScreenState: ScreenState) {
        state = state.copy(
            settings = settingsRepository.settings.first(),
            screenState = initialScreenState,
            appRulesByPackageName = installedAppRepository.observeApps().first().toRuntimeRuleMap(),
        )
        startCollectors()
        runtimeEnvironmentMonitor.register(scope)
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        reporter.markServiceStarted(initialScreenState)
        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = automationRuleEngine,
            immediate = true,
        )
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
                    send(RuntimeEvent.AppsChanged(apps.toRuntimeRuleMap()))
                }
            }
        }
    }

    private suspend fun handleStopped(
        finalScreenState: ScreenState,
        ack: CompletableDeferred<Unit>?,
    ) {
        scheduler.cancelAll()
        settingsJob?.cancel()
        appsJob?.cancel()
        settingsJob = null
        appsJob = null
        runtimeEnvironmentMonitor.unregister()
        reporter.markServiceStopped(finalScreenState)
        ack?.complete(Unit)
    }

    private suspend fun handleSettingsChanged(settings: UserSettings) {
        val previous = state.settings
        state = state.copy(settings = settings)
        if (!settings.automationEnabled) {
            scheduler.cancelForegroundProbe()
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
            scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = true)
            scheduler.cancelTemporaryDisableExpiry()
            reporter.markServiceRunning()
            return
        }

        if (settings.isTemporaryDisableActive()) {
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
            scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = true)
            temporaryDisableHandler.scheduleTemporaryDisableExpiry(scheduler, settings)
        } else {
            scheduler.cancelTemporaryDisableExpiry()
        }

        if (previous.monitorForegroundWhenScreenOff != settings.monitorForegroundWhenScreenOff ||
            previous.automationEnabled != settings.automationEnabled ||
            previous.temporaryDisableMode != settings.temporaryDisableMode
        ) {
            scheduler.scheduleForegroundProbe(
                state = state,
                automationRuleEngine = automationRuleEngine,
                immediate = true,
            )
        }
    }

    private suspend fun handleScreenOff() {
        state = state.copy(screenState = ScreenState.ScreenOff)
        scheduler.cancelForegroundProbe()
        if (state.settings.temporaryDisableMode == AutomationDisableMode.UntilScreenOff &&
            state.settings.isTemporaryDisableActive()
        ) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = "已息屏，恢复自动化",
            )
        }
        reporter.markServiceRunning(ScreenState.ScreenOff)
        disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = automationRuleEngine,
            immediate = false,
        )
    }

    private suspend fun handleNetworkChanged() {
        val previousSnapshot = reporter.snapshot.first()
        runtimeEnvironmentMonitor.refreshSnapshot()
        accessRepository.syncCurrentNetworkControlState()
        val updatedSnapshot = reporter.snapshot.first()
        state = unexpectedNetworkChangeGuard.handleNetworkStateObserved(
            state = state,
            previousSnapshot = previousSnapshot,
            updatedSnapshot = updatedSnapshot,
        )
    }

    private suspend fun handleScreenWake(
        screenState: ScreenState,
        triggerSource: TriggerSource,
    ) {
        state = state.copy(screenState = screenState)
        scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = true)
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = "临时禁用已到期，恢复自动化",
            )
        }
        if (state.settings.isTemporaryDisableActive()) {
            scheduler.scheduleForegroundProbe(
                state = state,
                automationRuleEngine = automationRuleEngine,
                immediate = false,
            )
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
        state = foregroundAutomationHandler.automationTick(
            state = state,
            scheduler = scheduler,
            triggerSource = triggerSource,
            allowReconnectWhenTargetAppAlreadyActive = allowReconnectWhenTargetAppAlreadyActive,
        )
        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = automationRuleEngine,
            immediate = false,
        )
    }

    private suspend fun handleForegroundProbeTick() {
        scheduler.markForegroundProbeConsumed()
        if (!state.settings.automationEnabled) {
            return
        }
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = "临时禁用已到期，恢复自动化",
            )
        }
        state = foregroundAutomationHandler.automationTick(
            state = state,
            scheduler = scheduler,
        )
        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = automationRuleEngine,
            immediate = false,
        )
    }

    private suspend fun handleTemporaryDisableExpired() {
        scheduler.markTemporaryDisableExpiryConsumed()
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = "临时禁用已到期，恢复自动化",
            )
            disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
            scheduler.scheduleForegroundProbe(
                state = state,
                automationRuleEngine = automationRuleEngine,
                immediate = true,
            )
        }
    }

    companion object {
        private const val LOG_TAG = "SmartFlightRuntime"

        fun foregroundProbeIntervalMillis(screenState: ScreenState): Long =
            RuntimeTaskScheduler.foregroundProbeIntervalMillis(screenState)
    }
}
