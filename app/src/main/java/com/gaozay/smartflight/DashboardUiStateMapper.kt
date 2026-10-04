package com.gaozay.smartflight

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.runtime.buildRuntimeSummary
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.isAutomationEffectivelyEnabled
import com.gaozay.smartflight.settings.temporaryDisableSummary
import javax.inject.Inject

class DashboardUiStateMapper @Inject constructor() {
    fun buildSmartFlightUiState(
        settings: UserSettings,
        runtimeSnapshot: RuntimeSnapshot,
        appCount: Int,
        logCount: Int,
        accessGateState: AccessGateState,
        recentLogs: List<ExecutionLogEntity>,
    ): SmartFlightUiState =
        SmartFlightUiState(
            accessGateState = accessGateState,
            settings = settings,
            advancedAccess = accessGateState.advancedAccess.selectedExecutorType.label,
            currentMode = settings.networkControlMode.label,
            automationEnabled = settings.isAutomationEffectivelyEnabled(),
            automationDisabled = !settings.automationEnabled || settings.temporaryDisableSummary() != null,
            automationDisableSummary = settings.temporaryDisableSummary()
                ?: if (settings.automationEnabled) null else AppStrings.get(R.string.permanently_disabled),
            monitorForegroundWhenScreenOff = settings.monitorForegroundWhenScreenOff,
            foregroundApp = runtimeSnapshot.currentForegroundAppLabel
                ?: runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
            foregroundActivity = runtimeSnapshot.currentForegroundActivityName,
            foregroundActivitySource = runtimeSnapshot.foregroundInfoSource,
            foregroundEventTimestampMillis = runtimeSnapshot.foregroundEventTimestampMillis,
            foregroundRuleReason = runtimeSnapshot.foregroundRuleReason,
            foregroundRuleLayer = runtimeSnapshot.foregroundRuleLayer,
            runtimeExecutor = runtimeSnapshot.activeExecutorType.label,
            runtimeLastCheck = runtimeSnapshot.runtimeStatusSummary,
            runtimeLastResult = runtimeSnapshot.runtimeStatusResult.label,
            runtimeUpdatedAtMillis = runtimeSnapshot.updatedAtMillis,
            unifiedNetworkState = runtimeSnapshot.unifiedNetworkState.label,
            wifiStatus = buildWifiStatus(runtimeSnapshot),
            bluetoothStatus = buildBluetoothStatus(runtimeSnapshot),
            mobileDataStatus = buildMobileDataStatus(runtimeSnapshot),
            bluetoothReadable = runtimeSnapshot.isBluetoothStateReadable,
            executorDiagnostics = emptyList(),
            recentExecutionLogs = recentLogs.map { it.toUiItem() },
            triggerSummary = buildString {
                append(buildRuntimeSummary(settings, runtimeSnapshot))
                append(AppStrings.get(R.string.dashboard_app_count_suffix))
                append(appCount)
                append(AppStrings.get(R.string.dashboard_log_count_suffix))
                append(logCount)
            },
        )
}

fun ExecutionLogEntity.toUiItem(): ExecutionLogItem {
    val actionLabel = when {
        AppStrings.hasTranslatedPrefix(errorMessage, R.string.probe_airplane_mode_prefix) ||
            AppStrings.hasTranslatedPrefix(errorMessage, R.string.probe_mobile_data_prefix) -> AppStrings.get(R.string.state_probe)
        actionType == ExecutionAction.ReconnectNow.name -> AppStrings.get(R.string.reconnect_now)
        actionType == ExecutionAction.DisconnectNow.name -> AppStrings.get(R.string.disconnect_now)
        actionType == ExecutionAction.DoNothing.name -> AppStrings.get(R.string.no_action_taken)
        else -> runCatching { enumValueOf<ExecutionAction>(actionType).label }.getOrDefault(actionType)
    }
    val executorLabel = runCatching { enumValueOf<ExecutorType>(executorType).label }.getOrDefault(executorType)
    val resultLabel = when (result) {
        ExecutionResult.Success.name -> AppStrings.get(R.string.success)
        ExecutionResult.Failed.name -> AppStrings.get(R.string.failed)
        ExecutionResult.Pending.name -> AppStrings.get(R.string.pending)
        ExecutionResult.PartialSuccess.name -> AppStrings.get(R.string.partial_success)
        ExecutionResult.Skipped.name -> AppStrings.get(R.string.skipped)
        else -> runCatching { enumValueOf<ExecutionResult>(result).label }.getOrDefault(result)
    }
    return ExecutionLogItem(
        timestampMillis = timestampMillis,
        action = actionLabel,
        executor = executorLabel,
        result = resultLabel,
        detail = buildString {
            append(errorMessage ?: AppStrings.get(R.string.no_additional_information))
            foregroundActivityName?.let { append("\n").append(foregroundPackageName.orEmpty()).append(" / ").append(it) }
            foregroundRuleLayer?.let { append("\n").append(AppStrings.get(if (it == "Activity") R.string.activity_match_override else R.string.activity_match_app_default)) }
            foregroundRuleReason?.let { value ->
                com.gaozay.smartflight.runtime.ActivityMatchReason.entries.find { it.name == value }?.let { append(" · ").append(it.label) }
            }
        },
    )
}

fun buildWifiStatus(snapshot: RuntimeSnapshot): String = when {
    snapshot.isWifiConnected -> AppStrings.get(R.string.connected)
    snapshot.isWifiEnabled -> AppStrings.get(R.string.enabled_not_connected)
    else -> AppStrings.get(R.string.disabled)
}

fun buildBluetoothStatus(snapshot: RuntimeSnapshot): String =
    if (!snapshot.isBluetoothStateReadable) {
        AppStrings.get(R.string.permission_not_granted_state_unavailable)
    } else if (snapshot.isBluetoothEnabled) {
        AppStrings.get(R.string.enabled)
    } else {
        AppStrings.get(R.string.disabled)
    }

fun buildMobileDataStatus(snapshot: RuntimeSnapshot): String =
    when (snapshot.isMobileDataEnabled) {
        true -> AppStrings.get(R.string.enabled)
        false -> AppStrings.get(R.string.disabled)
        null -> AppStrings.get(R.string.unknown)
    }
