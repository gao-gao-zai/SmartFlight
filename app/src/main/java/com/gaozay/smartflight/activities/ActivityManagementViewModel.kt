package com.gaozay.smartflight.activities

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.R
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.*
import com.gaozay.smartflight.settings.ForegroundMonitorMode
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ActivityManagementState(
    val packageName: String? = null,
    val app: InstalledAppEntity? = null,
    val details: ActivityDetails = ActivityDetails(),
    val isScanning: Boolean = false,
    val isSaving: Boolean = false,
    val savedSequence: Int = 0,
    val error: String? = null,
    val foreground: ForegroundAppInfo? = null,
    val monitorMode: ForegroundMonitorMode = ForegroundMonitorMode.Auto,
    val usageGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val recording: ActivityRecordingState = ActivityRecordingState(),
) {
    val canRecord: Boolean get() = when (monitorMode) {
        ForegroundMonitorMode.Auto -> usageGranted || accessibilityGranted
        ForegroundMonitorMode.Accessibility -> accessibilityGranted
        ForegroundMonitorMode.UsageStats -> usageGranted
    }
}

@HiltViewModel
class ActivityManagementViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val apps: InstalledAppRepository,
    private val foreground: HybridForegroundAppSource,
    private val observations: ForegroundObservationStore,
    private val settings: SettingsRepository,
    private val access: AccessRepository,
    private val recorder: ActivityRecorder,
) : ViewModel() {
    private val current = MutableStateFlow(ActivityManagementState())
    val state = current.asStateFlow()
    private var detailsJob: Job? = null
    private var scanJob: Job? = null

    init {
        viewModelScope.launch { observations.latest.collect { value -> current.update { it.copy(foreground = value) } } }
        viewModelScope.launch { recorder.state.collect { value -> current.update { it.copy(recording = value) } } }
        viewModelScope.launch { settings.settings.collect { value -> current.update { it.copy(monitorMode = value.foregroundMonitorMode) } } }
        viewModelScope.launch { access.accessGateState.collect { value -> current.update { it.copy(
            usageGranted = value.usageStatsAccess.satisfiesRequirement, accessibilityGranted = value.accessibilityAccess.satisfiesRequirement) } } }
    }
    fun selectApp(packageName: String?) {
        detailsJob?.cancel()
        scanJob?.cancel()
        current.update { it.copy(packageName = packageName, app = null, details = ActivityDetails(), isScanning = false, isSaving = false, error = null) }
        if (packageName == null) { recorder.stop(); return }
        detailsJob = viewModelScope.launch {
            combine(repository.observeDetails(packageName), apps.observeApps()) { details, entries ->
                details to entries.find { it.packageName == packageName }
            }.collect { (details, app) -> if (current.value.packageName == packageName) current.update { it.copy(details = details, app = app) } }
        }
        scanJob = viewModelScope.launch { if (!repository.isTracked(packageName)) scan(packageName) }
    }
    fun refresh() { current.value.packageName?.let { pkg -> scanJob?.cancel(); scanJob = viewModelScope.launch { scan(pkg) } } }
    private suspend fun scan(pkg: String) {
        current.update { it.copy(isScanning = true, error = null) }
        try { repository.refreshActivities(pkg) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (current.value.packageName == pkg) current.update { it.copy(error = AppStrings.get(R.string.scan_failed, error.message.orEmpty())) } }
        finally { if (current.value.packageName == pkg) current.update { it.copy(isScanning = false) } }
    }
    fun save(className: String, mode: ActivityRuleMode, note: String) {
        val pkg = current.value.packageName ?: return
        viewModelScope.launch {
            current.update { it.copy(isSaving = true, error = null) }
            try { repository.saveRule(pkg, className, mode, note); current.update { it.copy(savedSequence = it.savedSequence + 1) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { current.update { it.copy(error = if (error is UnverifiedActivityException) AppStrings.get(R.string.activity_runtime_verify) else AppStrings.get(R.string.activity_save_failed, error.message.orEmpty())) } }
            finally { current.update { it.copy(isSaving = false) } }
        }
    }
    fun setEnabled(enabled: Boolean) = mutate { pkg -> repository.setRulesEnabled(pkg, enabled) }
    fun acknowledgeReview() = mutate { pkg -> repository.acknowledgeReview(pkg) }
    private fun mutate(action: suspend (String) -> Unit) {
        val pkg = current.value.packageName ?: return
        viewModelScope.launch {
            try { action(pkg) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { current.update { it.copy(error = AppStrings.get(R.string.activity_save_failed, error.message.orEmpty())) } }
        }
    }
    fun startRecording(launch: Boolean) {
        val pkg = current.value.packageName ?: return
        viewModelScope.launch { recorder.start(pkg, launch) }
    }
    fun stopRecording() = recorder.stop()
    fun refreshIdentification() {
        viewModelScope.launch(Dispatchers.IO) {
            try { foreground.detect(current.value.monitorMode, confirm = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { current.update { it.copy(error = AppStrings.get(R.string.activity_identify_failed, error.message.orEmpty())) } }
        }
    }
}
