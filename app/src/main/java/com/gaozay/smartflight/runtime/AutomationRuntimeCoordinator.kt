package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.activities.ActivityRepository
import com.gaozay.smartflight.activities.ActivityRuntimeRule
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
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
class AutomationRuntimeCoordinator internal constructor(
    private val settingsRepository: SettingsRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val activityRules: () -> Flow<List<ActivityRuntimeRule>>,
    private val accessRepository: AccessRepository,
    private val automationRuleEngine: AutomationRuleEngine,
    private val runtimeEnvironmentMonitor: RuntimeEnvironmentSource,
    private val hybridForegroundAppSource: RuntimeForegroundSource,
    private val reporter: RuntimeSnapshotReporter,
    private val foregroundAutomationHandler: ForegroundAutomationHandler,
    private val disconnectAutomationHandler: DisconnectAutomationHandler,
    private val temporaryDisableHandler: TemporaryDisableHandler,
    private val unexpectedNetworkChangeGuard: UnexpectedNetworkChangeGuard,
    private val scope: CoroutineScope,
    monotonicTimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    @Inject constructor(
        settingsRepository: SettingsRepository,
        installedAppRepository: InstalledAppRepository,
        activityRepository: ActivityRepository,
        accessRepository: AccessRepository,
        automationRuleEngine: AutomationRuleEngine,
        runtimeEnvironmentMonitor: RuntimeEnvironmentMonitor,
        hybridForegroundAppSource: HybridForegroundAppSource,
        reporter: RuntimeSnapshotReporter,
        foregroundAutomationHandler: ForegroundAutomationHandler,
        disconnectAutomationHandler: DisconnectAutomationHandler,
        temporaryDisableHandler: TemporaryDisableHandler,
        unexpectedNetworkChangeGuard: UnexpectedNetworkChangeGuard,
    ) : this(settingsRepository, installedAppRepository, activityRepository::observeRuntimeRules,
        accessRepository, automationRuleEngine, runtimeEnvironmentMonitor, hybridForegroundAppSource,
        reporter, foregroundAutomationHandler, disconnectAutomationHandler, temporaryDisableHandler,
        unexpectedNetworkChangeGuard, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private data class QueuedEvent(val generation: Long, val event: RuntimeEvent)
    private val events = Channel<QueuedEvent>(Channel.UNLIMITED)
    private val pendingDecisionEvents = AtomicInteger()
    @Volatile private var lifecycleGeneration = 0L
    private val scheduler = RuntimeTaskScheduler(
        scope = scope,
        send = ::send,
        reporter = reporter,
        monotonicTimeMillis = monotonicTimeMillis,
    )

    private var eventLoopJob: Job? = null
    private var settingsJob: Job? = null
    private var appsJob: Job? = null
    private var activityRulesJob: Job? = null
    private var state = RuntimeState()
    @Volatile
    private var runtimeActive = false

    @Synchronized
    fun start(initialScreenState: ScreenState) {
        if (runtimeActive) return
        lifecycleGeneration++
        if (eventLoopJob?.isActive != true) {
            eventLoopJob = scope.launch {
                for ((generation, event) in events) {
                    try {
                        if (generation != lifecycleGeneration) {
                            (event as? RuntimeEvent.Stopped)?.ack?.complete(Unit)
                            continue
                        }
                        runCatching {
                            handleEvent(event)
                        }.onFailure { throwable ->
                            Log.e(LOG_TAG, "runtime event failed event=${event.nameForLog()}", throwable)
                            reporter.reportEventFailure(throwable)
                        }
                    } finally {
                        if (event.affectsDisconnectDecision()) pendingDecisionEvents.decrementAndGet()
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

    @Synchronized
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
        synchronized(this) {
            runtimeActive = false
            send(RuntimeEvent.Stopped(finalScreenState, ack))
        }
        ack.await()
    }

    private fun send(event: RuntimeEvent, generation: Long = lifecycleGeneration) {
        if (event.affectsDisconnectDecision()) pendingDecisionEvents.incrementAndGet()
        if (events.trySend(QueuedEvent(generation, event)).isFailure) {
            if (event.affectsDisconnectDecision()) pendingDecisionEvents.decrementAndGet()
            Log.w(LOG_TAG, "runtime event dropped event=${event.nameForLog()}")
        }
    }

    private fun isRuntimeAcceptingEvents(): Boolean =
        runtimeActive && eventLoopJob?.isActive == true

    private suspend fun handleEvent(event: RuntimeEvent) {
        if (!runtimeActive && event !is RuntimeEvent.Started && event !is RuntimeEvent.Stopped) return
        when (event) {
            is RuntimeEvent.Started -> handleStarted(event.initialScreenState)
            is RuntimeEvent.Stopped -> try {
                handleStopped(event.finalScreenState)
            } finally {
                event.ack?.complete(Unit)
            }
            RuntimeEvent.ScreenOff -> handleScreenOff()
            RuntimeEvent.ScreenOn -> handleScreenWake(
                screenState = ScreenState.ScreenOn,
                triggerSource = TriggerSource.ScreenOn,
            )
            RuntimeEvent.UserUnlocked -> handleScreenWake(
                screenState = ScreenState.Unlocked,
                triggerSource = TriggerSource.UserUnlocked,
            )
            is RuntimeEvent.ForegroundProbeTick -> handleForegroundProbeTick(event)
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
            is RuntimeEvent.TemporaryDisableExpired -> handleTemporaryDisableExpired(event)
            is RuntimeEvent.ScreenOffDisconnectDue -> {
                if (!scheduler.isScreenOffDisconnectDueCurrent(event)) return
                if (pendingDecisionEvents.get() > 0) {
                    send(event)
                    return
                }
                disconnectAutomationHandler.handleScreenOffDisconnectDue(state, scheduler, event)
            }
            is RuntimeEvent.AppExitDisconnectDue -> {
                if (!scheduler.isAppExitDisconnectDueCurrent(event)) return
                // Process already queued rule, screen, source and network changes
                // before deciding whether this countdown may still cut off.
                if (pendingDecisionEvents.get() > 0) {
                    send(event)
                    return
                }
                state = foregroundAutomationHandler.automationTick(state, scheduler, requireForegroundConfirmation = true)
                disconnectAutomationHandler.handleAppExitDisconnectDue(state, scheduler, event)
            }
        }
    }

    private suspend fun handleStarted(initialScreenState: ScreenState) {
        val generation = lifecycleGeneration
        scheduler.cancelAll()
        state = state.copy(
            settings = settingsRepository.settings.first(),
            screenState = initialScreenState,
            appRulesByPackageName = installedAppRepository.observeApps().first().toRuntimeRuleMap(),
            activityRules = activityRules().first(),
            lastTargetAppActive = null,
            lastKnownForegroundApp = null,
            appExitAwaitingForegroundConfirmation = false,
        )
        hybridForegroundAppSource.monitorMode = state.settings.foregroundMonitorMode
        hybridForegroundAppSource.confirmActivity = state.activityRules.any { it.rulesEnabled && it.isValid }
        startCollectors(generation)
        runtimeEnvironmentMonitor.register { if (runtimeActive) send(RuntimeEvent.NetworkChanged, generation) }
        accessRepository.refresh()
        accessRepository.syncCurrentNetworkControlState()
        runtimeEnvironmentMonitor.refreshSnapshot()
        reporter.markServiceStarted(initialScreenState)
        disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
        scheduleForegroundObservation(immediate = true)
    }

    private fun startCollectors(generation: Long) {
        settingsJob?.cancel()
        appsJob?.cancel()
        activityRulesJob?.cancel()
        settingsJob = null
        appsJob = null
        activityRulesJob = null
        activityRulesJob = scope.launch {
            activityRules().collect { send(RuntimeEvent.ActivityRulesChanged(it), generation) }
        }
        settingsJob = scope.launch {
            settingsRepository.settings.collect { send(RuntimeEvent.SettingsChanged(it), generation) }
        }
        appsJob = scope.launch {
            installedAppRepository.observeApps().collect { send(RuntimeEvent.AppsChanged(it.toRuntimeRuleMap()), generation) }
        }
    }

    private suspend fun handleStopped(
        finalScreenState: ScreenState,
    ) {
        scheduler.cancelAll()
        settingsJob?.cancel()
        appsJob?.cancel()
        activityRulesJob?.cancel()
        settingsJob = null
        appsJob = null
        activityRulesJob = null
        runtimeEnvironmentMonitor.unregister()
        reporter.markServiceStopped(finalScreenState)
    }

    private suspend fun handleSettingsChanged(settings: UserSettings) {
        val previous = state.settings
        state = state.copy(settings = settings)
        val appExitPolicyChanged = previous.appExitDelaySeconds != settings.appExitDelaySeconds ||
            previous.appExitDisconnectEnabled != settings.appExitDisconnectEnabled ||
            previous.automationEnabled != settings.automationEnabled ||
            previous.temporaryDisableMode != settings.temporaryDisableMode ||
            previous.temporaryDisableUntilMillis != settings.temporaryDisableUntilMillis
        if (appExitPolicyChanged) {
            val wasPending = scheduler.isAppExitDisconnectActive
            scheduler.cancelAppExitDisconnect(reporter, updateRuntimeState = false)
            state = state.copy(
                appExitAwaitingForegroundConfirmation = state.appExitAwaitingForegroundConfirmation || wasPending,
            )
        }
        // A changed delay starts a fresh full countdown; repeated unchanged settings preserve it.
        if (previous.screenOffDelaySeconds != settings.screenOffDelaySeconds) {
            scheduler.cancelScreenOffDisconnect(state, reporter, updateRuntimeState = false)
        }
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

        disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)

        if (previous.monitorForegroundWhenScreenOff != settings.monitorForegroundWhenScreenOff ||
            previous.automationEnabled != settings.automationEnabled ||
            previous.temporaryDisableMode != settings.temporaryDisableMode ||
            previous.foregroundMonitorMode != settings.foregroundMonitorMode ||
            appExitPolicyChanged
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
        disconnectAutomationHandler.scheduleScreenOffDisconnectIfNeeded(state, scheduler)
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

    private suspend fun handleForegroundProbeTick(event: RuntimeEvent.ForegroundProbeTick) {
        if (!scheduler.markForegroundProbeConsumed(event)) return
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
            foregroundAppOverride = hybridForegroundAppSource.detectForEvent(foregroundApp),
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
            ForegroundMonitorMode.Auto -> hybridForegroundAppSource.accessibilityConnected
            ForegroundMonitorMode.Accessibility -> hybridForegroundAppSource.accessibilityConnected
            ForegroundMonitorMode.UsageStats -> false
        }

    private fun shouldSuppressPeriodicForegroundProbe(): Boolean =
        when (state.settings.foregroundMonitorMode) {
            ForegroundMonitorMode.Auto -> false
            ForegroundMonitorMode.Accessibility -> true
            ForegroundMonitorMode.UsageStats -> false
        }

    private suspend fun handleTemporaryDisableExpired(event: RuntimeEvent.TemporaryDisableExpired) {
        if (!scheduler.markTemporaryDisableExpiryConsumed(event)) return
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
