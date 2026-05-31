package com.gaozay.smartflight

import com.gaozay.smartflight.apps.AppsUiState
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.buildAppsUiState
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class MainUiStateAssembler @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runtimeStatusRepository: RuntimeStatusRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val accessRepository: AccessRepository,
    private val appsManagementController: AppsManagementController,
    private val dashboardUiStateMapper: DashboardUiStateMapper,
) {
    fun smartFlightUiState(): Flow<SmartFlightUiState> {
        val recentExecutionLogs = executionLogRepository.observeRecentLogs(limit = 6)
        return combine(
            settingsRepository.settings,
            runtimeStatusRepository.snapshot,
            installedAppRepository.observeAppCount(),
            executionLogRepository.observeLogCount(),
            accessRepository.accessGateState,
        ) { settings, runtimeSnapshot, appCount, logCount, accessGateState ->
            UiStateBase(
                settings = settings,
                runtimeSnapshot = runtimeSnapshot,
                appCount = appCount,
                logCount = logCount,
                accessGateState = accessGateState,
            )
        }.combine(recentExecutionLogs) { base, recentLogs ->
            dashboardUiStateMapper.buildSmartFlightUiState(
                settings = base.settings,
                runtimeSnapshot = base.runtimeSnapshot,
                appCount = base.appCount,
                logCount = base.logCount,
                accessGateState = base.accessGateState,
                recentLogs = recentLogs,
            )
        }
    }

    fun appsUiState(): Flow<AppsUiState> =
        combine(
            installedAppRepository.observeApps(),
            appsManagementController.appQuery,
            appsManagementController.appFilterStateFlow(),
            appsManagementController.appScanning,
            appsManagementController.appLastScanSummary,
        ) { apps, query, filterState, isScanning, lastScanSummary ->
            buildAppsUiState(
                apps = apps,
                query = query,
                filterState = filterState,
                isScanning = isScanning,
                lastScanSummary = lastScanSummary,
            )
        }
}

private data class UiStateBase(
    val settings: UserSettings,
    val runtimeSnapshot: RuntimeSnapshot,
    val appCount: Int,
    val logCount: Int,
    val accessGateState: AccessGateState,
)
