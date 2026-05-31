package com.gaozay.smartflight

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
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
                ?: if (settings.automationEnabled) null else "已永久禁用",
            monitorForegroundWhenScreenOff = settings.monitorForegroundWhenScreenOff,
            foregroundApp = runtimeSnapshot.currentForegroundAppLabel
                ?: runtimeSnapshot.currentForegroundPackageName
                ?: "Not connected yet",
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
                append(" · Apps: ")
                append(appCount)
                append(" · Logs: ")
                append(logCount)
            },
        )
}

fun ExecutionLogEntity.toUiItem(): ExecutionLogItem {
    val actionLabel = when {
        errorMessage?.startsWith("飞行模式状态探测：") == true ||
            errorMessage?.startsWith("移动数据状态探测：") == true -> "状态探测"
        actionType == ExecutionAction.ReconnectNow.name -> "立即恢复联网"
        actionType == ExecutionAction.DisconnectNow.name -> "立即断网"
        actionType == ExecutionAction.DoNothing.name -> "未执行动作"
        else -> runCatching { enumValueOf<ExecutionAction>(actionType).label }.getOrDefault(actionType)
    }
    val executorLabel = runCatching { enumValueOf<ExecutorType>(executorType).label }.getOrDefault(executorType)
    val resultLabel = when (result) {
        ExecutionResult.Success.name -> "成功"
        ExecutionResult.Failed.name -> "失败"
        ExecutionResult.Pending.name -> "待定"
        ExecutionResult.PartialSuccess.name -> "部分成功"
        ExecutionResult.Skipped.name -> "已跳过"
        else -> runCatching { enumValueOf<ExecutionResult>(result).label }.getOrDefault(result)
    }
    return ExecutionLogItem(
        timestampMillis = timestampMillis,
        action = actionLabel,
        executor = executorLabel,
        result = resultLabel,
        detail = errorMessage ?: "无附加信息",
    )
}

fun buildWifiStatus(snapshot: RuntimeSnapshot): String = when {
    snapshot.isWifiConnected -> "已连接"
    snapshot.isWifiEnabled -> "已开启，未连接"
    else -> "已关闭"
}

fun buildBluetoothStatus(snapshot: RuntimeSnapshot): String =
    if (!snapshot.isBluetoothStateReadable) {
        "未授权，不可读"
    } else if (snapshot.isBluetoothEnabled) {
        "已开启"
    } else {
        "已关闭"
    }

fun buildMobileDataStatus(snapshot: RuntimeSnapshot): String =
    when (snapshot.isMobileDataEnabled) {
        true -> "已开启"
        false -> "已关闭"
        null -> "未知"
    }

data class UiStateBase(
    val settings: UserSettings,
    val runtimeSnapshot: RuntimeSnapshot,
    val appCount: Int,
    val logCount: Int,
    val accessGateState: AccessGateState,
)
