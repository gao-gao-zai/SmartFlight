package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.activities.ActivityRepository
import android.util.Log
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isTemporaryDisableActive
import com.gaozay.smartflight.settings.shouldClearExpiredTemporaryDisable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class AutomationRuntimeCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val activityRepository: ActivityRepository,
    private val accessRepository: AccessRepository,
    private val automationRuleEngine: AutomationRuleEngine,
    private val runtimeEnvironmentMonitor: RuntimeEnvironmentMonitor,
    private val accessibilityForegroundAppTracker: AccessibilityForegroundAppTracker,
    private val hybridForegroundAppSource: HybridForegroundAppSource,
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
    private var activityRulesJob: Job? = null
    private var state = RuntimeState()
    @Volatile
    private var runtimeActive = false

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
                }
            }
        }
        runtimeActive = true
        send(RuntimeEvent.Started(initialScreenState))
    }

    fun onScreenOff() {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.ScreenOff)
        }
    }

    fun onScreenOn() {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.ScreenOn)
        }
    }

    fun onUserUnlocked() {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.UserUnlocked)
        }
    }

    fun onNetworkChanged() {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.NetworkChanged)
        }
    }

    fun onForegroundAppChanged(foregroundApp: ForegroundAppInfo) {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.ForegroundAppChanged(foregroundApp))
        }
    }

    fun onForegroundEventSourceChanged() {
        if (isRuntimeAcceptingEvents()) {
            send(RuntimeEvent.ForegroundEventSourceChanged)
        }
    }

    fun requestStop(finalScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            return
        }
        runtimeActive = false
        send(RuntimeEvent.Stopped(finalScreenState, ack = null))
    }

    suspend fun stop(finalScreenState: ScreenState) {
        if (eventLoopJob?.isActive != true) {
            return
        }
        val ack = CompletableDeferred<Unit>()
        runtimeActive = false
        send(RuntimeEvent.Stopped(finalScreenState, ack))
        ack.await()
    }

    private fun send(event: RuntimeEvent) {
        if (events.trySend(event).isFailure) {
            Log.w(LOG_TAG, "runtime event dropped event=${event.nameForLog()}")
        }
    }

    private fun isRuntimeAcceptingEvents(): Boolean =
        runtimeActive && eventLoopJob?.isActive == true

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
            is RuntimeEvent.ForegroundAppChanged -> handleForegroundAppChanged(event.foregroundApp)
            RuntimeEvent.ForegroundEventSourceChanged -> handleForegroundEventSourceChanged()
            is RuntimeEvent.SettingsChanged -> handleSettingsChanged(event.settings)
            is RuntimeEvent.AppsChanged -> {
                val changed = state.appRulesByPackageName != event.appRulesByPackageName
                state = state.copy(appRulesByPackageName = event.appRulesByPackageName)
                if (changed && state.lastTargetAppActive != null) scheduleForegroundObservation(immediate = true)
            }
            is RuntimeEvent.ActivityRulesChanged -> {
                val changed = state.activityRules != event.rules
                state = state.copy(activityRules = event.rules)
                hybridForegroundAppSource.confirmActivity = event.rules.any { it.rulesEnabled && it.isValid }
                if (changed && state.lastTargetAppActive != null) scheduleForegroundObservation(immediate = true)
            }
            RuntimeEvent.NetworkChanged -> handleNetworkChanged()
            RuntimeEvent.TemporaryDisableExpired -> handleTemporaryDisableExpired()
            RuntimeEvent.ScreenOffDisconnectDue -> {
                disconnectAutomationHandler.handleScreenOffDisconnectDue(state, scheduler)
            }
            RuntimeEvent.AppExitDisconnectDue -> {
                if (state.activityRules.isNotEmpty()) state = foregroundAutomationHandler.automationTick(state, scheduler)
                disconnectAutomationHandler.handleAppExitDisconnectDue(state, scheduler)
            }
        }
    }

    private suspend fun handleStarted(initialScreenState: ScreenState) {
        runtimeActive = true
        state = state.copy(
            settings = settingsRepository.settings.first(),
            screenState = initialScreenState,
            appRulesByPackageName = installedAppRepository.observeApps().first().toRuntimeRuleMap(),
            activityRules = activityRepository.observeRuntimeRules().first(),
        )
        hybridForegroundAppSource.monitorMode = state.settings.foregroundMonitorMode
        hybridForegroundAppSource.confirmActivity = state.activityRules.any { it.rulesEnabled && it.isValid }
        startCollectors()
        runtimeEnvironmentMonitor.register(scope)
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        reporter.markServiceStarted(initialScreenState)
        scheduleForegroundObservation(immediate = true)
    }

    private fun startCollectors() {
        if (activityRulesJob?.isActive != true) {
            activityRulesJob = scope.launch { activityRepository.observeRuntimeRules().collect { send(RuntimeEvent.ActivityRulesChanged(it)) } }
        }
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
        runtimeActive = false
        scheduler.cancelAll()
        settingsJob?.cancel()
        appsJob?.cancel()
        activityRulesJob?.cancel()
        settingsJob = null
        appsJob = null
        activityRulesJob = null
        runtimeEnvironmentMonitor.unregister()
        reporter.markServiceStopped(finalScreenState)
        ack?.complete(Unit)
    }

    private suspend fun handleSettingsChanged(settings: UserSettings) {
        val previous = state.settings
        state = state.copy(settings = settings)
        if (previous.foregroundMonitorMode != settings.foregroundMonitorMode) {
            state = state.copy(lastKnownForegroundApp = state.lastKnownForegroundApp?.withoutActivityConfirmation())
        }
        hybridForegroundAppSource.monitorMode = settings.foregroundMonitorMode
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
            previous.temporaryDisableMode != settings.temporaryDisableMode ||
            previous.foregroundMonitorMode != settings.foregroundMonitorMode
        ) {
            scheduler.cancelForegroundProbe()
            scheduleForegroundObservation(immediate = true)
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
                reason = AppStrings.get(R.string.screen_turned_off_automation_restored),
            )
        }
        reporter.markServiceRunning(ScreenState.ScreenOff)
        disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
        scheduleForegroundObservation(immediate = false)
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
                reason = AppStrings.get(R.string.temporary_disable_period_expired_automation_restored),
            )
        }
        if (state.settings.isTemporaryDisableActive()) {
            scheduleForegroundObservation(immediate = false)
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
        scheduleForegroundObservation(immediate = false)
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
                reason = AppStrings.get(R.string.temporary_disable_period_expired_automation_restored),
            )
        }
        state = foregroundAutomationHandler.automationTick(
            state = state,
            scheduler = scheduler,
        )
        scheduleForegroundObservation(immediate = false)
    }

    private suspend fun handleForegroundAppChanged(foregroundApp: ForegroundAppInfo) {
        if (!state.settings.automationEnabled) {
            return
        }
        if (!shouldUseForegroundEvents()) {
            return
        }
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = AppStrings.get(R.string.temporary_disable_period_expired_automation_restored),
            )
        }
        state = foregroundAutomationHandler.automationTick(
            state = state,
            scheduler = scheduler,
            foregroundAppOverride = if (hybridForegroundAppSource.confirmActivity) hybridForegroundAppSource.detect() ?: foregroundApp else foregroundApp,
        )
        scheduleForegroundObservation(immediate = false)
    }

    private fun handleForegroundEventSourceChanged() {
        state = state.copy(lastKnownForegroundApp = state.lastKnownForegroundApp?.withoutActivityConfirmation())
        if (shouldSuppressPeriodicForegroundProbe()) {
            scheduler.cancelForegroundProbe()
        }
        scheduleForegroundObservation(immediate = true)
    }

    private fun scheduleForegroundObservation(immediate: Boolean) {
        scheduler.scheduleForegroundProbe(
            state = state,
            automationRuleEngine = automationRuleEngine,
            immediate = immediate,
            eventDrivenForegroundAvailable = shouldSuppressPeriodicForegroundProbe(),
        )
    }

    private fun shouldUseForegroundEvents(): Boolean =
        when (state.settings.foregroundMonitorMode) {
            ForegroundMonitorMode.Auto -> accessibilityForegroundAppTracker.isServiceConnected
            ForegroundMonitorMode.Accessibility -> accessibilityForegroundAppTracker.isServiceConnected
            ForegroundMonitorMode.UsageStats -> false
        }

    private fun shouldSuppressPeriodicForegroundProbe(): Boolean =
        when (state.settings.foregroundMonitorMode) {
            ForegroundMonitorMode.Auto -> accessibilityForegroundAppTracker.isServiceConnected && !hybridForegroundAppSource.confirmActivity
            ForegroundMonitorMode.Accessibility -> true
            ForegroundMonitorMode.UsageStats -> false
        }

    private suspend fun handleTemporaryDisableExpired() {
        scheduler.markTemporaryDisableExpiryConsumed()
        if (state.settings.shouldClearExpiredTemporaryDisable()) {
            state = temporaryDisableHandler.clearTemporaryDisable(
                state = state,
                scheduler = scheduler,
                reason = AppStrings.get(R.string.temporary_disable_period_expired_automation_restored),
            )
            disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
            scheduleForegroundObservation(immediate = true)
        }
    }

    companion object {
        private const val LOG_TAG = "SmartFlightRuntime"

        fun foregroundProbeIntervalMillis(screenState: ScreenState): Long =
            RuntimeTaskScheduler.foregroundProbeIntervalMillis(screenState)
    }
}
