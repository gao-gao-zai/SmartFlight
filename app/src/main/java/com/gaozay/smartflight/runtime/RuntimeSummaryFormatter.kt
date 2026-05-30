package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.temporaryDisableSummary

internal fun buildRuntimeSummary(
    settings: UserSettings,
    snapshot: RuntimeSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val mode = settings.networkControlMode
    settings.temporaryDisableSummary(nowMillis)?.let { return it }
    if (!settings.automationEnabled &&
        snapshot.lastAction == ExecutionAction.DoNothing &&
        snapshot.lastActionResult == ExecutionResult.Pending
    ) {
        return "自动化已永久禁用"
    }
    if (snapshot.isAppExitDisconnectScheduled) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingAppExitDisconnectAtMillis,
            fallbackSeconds = settings.appExitDelaySeconds,
            nowMillis = nowMillis,
        )
        return "联网应用已离开前台，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.isScreenOffDisconnectScheduled) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingScreenOffDisconnectAtMillis,
            fallbackSeconds = settings.screenOffDelaySeconds,
            nowMillis = nowMillis,
        )
        return "屏幕已熄灭，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.screenState == ScreenState.ScreenOff &&
        !settings.monitorForegroundWhenScreenOff
    ) {
        return "屏幕已熄灭，已按设置暂停前台应用监听"
    }
    if (snapshot.lastAction == ExecutionAction.CancelScheduledDisconnect) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (snapshot.lastTriggerSource) {
                TriggerSource.UserUnlocked -> "用户已解锁，已取消待执行的息屏延迟断网"
                TriggerSource.ScreenOn -> "屏幕已点亮，已取消待执行的息屏延迟断网"
                TriggerSource.AppForegroundChanged -> "联网应用已重新进入前台，已取消待执行的离开应用延迟断网"
                else -> "已取消待执行的息屏延迟断网"
            }

            ExecutionResult.Skipped -> when (snapshot.lastTriggerSource) {
                TriggerSource.UserUnlocked -> "用户已解锁，当前没有待取消的息屏延迟断网"
                TriggerSource.ScreenOn -> "屏幕已点亮，当前没有待取消的息屏延迟断网"
                TriggerSource.AppForegroundChanged -> "当前没有待取消的离开应用延迟断网"
                else -> "当前没有待取消的息屏延迟断网"
            }

            else -> snapshot.lastActionReason
        }
    }
    if (snapshot.lastAction == ExecutionAction.ScheduleAppExitDisconnect) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingAppExitDisconnectAtMillis,
            fallbackSeconds = settings.appExitDelaySeconds,
            nowMillis = nowMillis,
        )
        return "联网应用已离开前台，将在 ${remainingSeconds} 秒后断网"
    }
    if (snapshot.lastAction == ExecutionAction.DoNothing &&
        snapshot.lastTriggerSource == TriggerSource.Manual
    ) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> buildProbeSuccessSummary(mode, snapshot)
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank { "${mode.label}状态探测失败" }
            else -> snapshot.lastActionReason.ifBlank { "${mode.label}状态待确认" }
        }
    }
    if (snapshot.lastAction == ExecutionAction.DisconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (mode) {
                NetworkControlMode.AirplaneMode -> "已开启飞行模式，当前处于断网状态"
                NetworkControlMode.MobileData -> "已关闭移动数据，当前处于断网状态${settings.mobileDataNoOpSuffix()}"
            }
            ExecutionResult.Skipped -> when (mode) {
                NetworkControlMode.AirplaneMode -> "飞行模式原本已开启，无需重复断网"
                NetworkControlMode.MobileData -> "移动数据原本已关闭，无需重复断网${settings.mobileDataNoOpSuffix()}"
            }
            ExecutionResult.PartialSuccess -> snapshot.lastActionReason.ifBlank { "${mode.label}写入后校验异常" }
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank {
                when (mode) {
                    NetworkControlMode.AirplaneMode -> "开启飞行模式失败"
                    NetworkControlMode.MobileData -> "关闭移动数据失败"
                }
            }
            else -> snapshot.lastActionReason.ifBlank { "正在执行断网" }
        }
    }
    if (snapshot.lastAction == ExecutionAction.ReconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (mode) {
                NetworkControlMode.AirplaneMode -> "已关闭飞行模式，当前已恢复联网"
                NetworkControlMode.MobileData -> "已开启移动数据，当前已恢复联网${settings.mobileDataNoOpSuffix()}"
            }
            ExecutionResult.Skipped -> when (mode) {
                NetworkControlMode.AirplaneMode -> "飞行模式原本已关闭，无需重复恢复联网"
                NetworkControlMode.MobileData -> "移动数据原本已开启，无需重复恢复联网${settings.mobileDataNoOpSuffix()}"
            }
            ExecutionResult.PartialSuccess -> snapshot.lastActionReason.ifBlank { "${mode.label}写入后校验异常" }
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank {
                when (mode) {
                    NetworkControlMode.AirplaneMode -> "关闭飞行模式失败"
                    NetworkControlMode.MobileData -> "开启移动数据失败"
                }
            }
            else -> snapshot.lastActionReason.ifBlank { "正在执行恢复联网" }
        }
    }
    return snapshot.lastActionReason
}

private fun remainingSeconds(
    pendingAtMillis: Long?,
    fallbackSeconds: Int,
    nowMillis: Long,
): Long = pendingAtMillis?.let {
    ((it - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
} ?: fallbackSeconds.toLong()

private fun buildProbeSuccessSummary(
    mode: NetworkControlMode,
    snapshot: RuntimeSnapshot,
): String = when (mode) {
    NetworkControlMode.AirplaneMode -> when (snapshot.isAirplaneModeEnabled) {
        true -> "飞行模式当前已开启"
        false -> "飞行模式当前已关闭"
        null -> "飞行模式状态已同步"
    }
    NetworkControlMode.MobileData -> when (snapshot.isMobileDataEnabled) {
        true -> "移动数据当前已开启"
        false -> "移动数据当前已关闭"
        null -> "移动数据状态已同步"
    }
}
