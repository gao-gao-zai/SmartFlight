package com.gaozay.smartflight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaozay.smartflight.apps.AppFilter
import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.status
import com.gaozay.smartflight.domain.model.AppListStatus
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    runtimeStatusRepository: RuntimeStatusRepository,
    private val installedAppRepository: InstalledAppRepository,
    executionLogRepository: ExecutionLogRepository,
    private val accessRepository: AccessRepository,
) : ViewModel() {
    private val appQuery = MutableStateFlow("")
    private val appFilter = MutableStateFlow(AppFilter.All)
    private val appScanning = MutableStateFlow(false)
    private val appLastScanSummary = MutableStateFlow("尚未扫描")

    val uiState: StateFlow<SmartFlightUiState> = combine(
        settingsRepository.settings,
        runtimeStatusRepository.snapshot,
        installedAppRepository.observeAppCount(),
        executionLogRepository.observeLogCount(),
        accessRepository.accessGateState,
    ) { settings, runtimeSnapshot, appCount, logCount, accessGateState ->
        SmartFlightUiState(
            accessGateState = accessGateState,
            advancedAccess = accessGateState.advancedAccess.selectedExecutorType.label,
            currentMode = settings.networkControlMode.label,
            foregroundApp = runtimeSnapshot.currentForegroundAppLabel
                ?: runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            triggerSummary = buildString {
                append(runtimeSnapshot.lastActionReason)
                append(" · Apps: ")
                append(appCount)
                append(" · Logs: ")
                append(logCount)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartFlightUiState(),
    )

    val appsUiState: StateFlow<AppsUiState> = combine(
        installedAppRepository.observeApps(),
        appQuery.asStateFlow(),
        appFilter.asStateFlow(),
        appScanning.asStateFlow(),
        appLastScanSummary.asStateFlow(),
    ) { apps, query, filter, isScanning, lastScanSummary ->
        val filteredApps = apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppFilter.All -> true
                AppFilter.Candidate -> app.status() == AppListStatus.Candidate
                AppFilter.Whitelist -> app.status() == AppListStatus.Whitelist
                AppFilter.Blacklist -> app.status() == AppListStatus.Blacklist
                AppFilter.Ignored -> app.status() == AppListStatus.Ignored
            }
            matchesQuery && matchesFilter
        }
        AppsUiState(
            apps = filteredApps,
            query = query,
            filter = filter,
            totalCount = apps.size,
            candidateCount = apps.count { it.status() == AppListStatus.Candidate },
            whitelistCount = apps.count { it.status() == AppListStatus.Whitelist },
            blacklistCount = apps.count { it.status() == AppListStatus.Blacklist },
            ignoredCount = apps.count { it.status() == AppListStatus.Ignored },
            isScanning = isScanning,
            lastScanSummary = lastScanSummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppsUiState(),
    )

    init {
        refreshAccessChecks()
    }

    fun refreshAccessChecks() {
        viewModelScope.launch {
            accessRepository.refresh()
        }
    }

    fun updateAppQuery(query: String) {
        appQuery.value = query
    }

    fun updateAppFilter(filter: AppFilter) {
        appFilter.value = filter
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            appScanning.value = true
            val count = runCatching {
                installedAppRepository.refreshInstalledApps()
            }.getOrElse {
                appLastScanSummary.value = "扫描失败：${it.message ?: "未知错误"}"
                appScanning.value = false
                return@launch
            }
            appLastScanSummary.value = "上次扫描发现 $count 个已安装应用"
            appScanning.value = false
        }
    }

    fun setAppListStatus(packageName: String, status: AppListStatus) {
        viewModelScope.launch {
            installedAppRepository.setListStatus(packageName, status)
        }
    }
}

@Immutable
data class SmartFlightUiState(
    val accessGateState: AccessGateState = AccessGateState(),
    val advancedAccess: String = "需要 Shizuku / ADB / Root",
    val currentMode: String = "飞行模式",
    val foregroundApp: String = "尚未连接",
    val triggerSummary: String = "项目已初始化，运行时引擎待接入。",
)

