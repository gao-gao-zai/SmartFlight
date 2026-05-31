package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.NetworkControlMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkControlCommandMapper @Inject constructor() {
    fun readCommandFor(mode: NetworkControlMode): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorReadonlyCommands.ReadAirplaneModeState
            NetworkControlMode.MobileData -> ExecutorReadonlyCommands.ReadMobileDataState
        }

    fun writeCommandFor(mode: NetworkControlMode, enabled: Boolean): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorWriteCommands.setAirplaneModeState(enabled)
            NetworkControlMode.MobileData -> ExecutorWriteCommands.setMobileDataEnabled(enabled)
        }

    fun labelFor(mode: NetworkControlMode): String =
        when (mode) {
            NetworkControlMode.AirplaneMode -> "飞行模式"
            NetworkControlMode.MobileData -> "移动数据"
        }

    fun alreadyInStateSummary(mode: NetworkControlMode, enabled: Boolean): String =
        when (mode) {
            NetworkControlMode.AirplaneMode ->
                if (enabled) "飞行模式已处于开启状态" else "飞行模式已处于关闭状态"
            NetworkControlMode.MobileData ->
                if (enabled) "移动数据已处于开启状态" else "移动数据已处于关闭状态"
        }

    fun enabledChangedSummary(mode: NetworkControlMode, enabled: Boolean): String =
        when (mode) {
            NetworkControlMode.AirplaneMode ->
                if (enabled) "飞行模式已开启" else "飞行模式已关闭"
            NetworkControlMode.MobileData ->
                if (enabled) "移动数据已开启" else "移动数据已关闭"
        }

    fun noReadExecutorSummary(mode: NetworkControlMode): String =
        "没有可用于读取${labelFor(mode)}状态的执行器"

    fun noToggleExecutorSummary(mode: NetworkControlMode): String =
        "没有可用于切换${labelFor(mode)}的执行器"

    fun unresolvedSetSummary(mode: NetworkControlMode): String =
        "无法解析当前${labelFor(mode)}状态，已取消设置"

    fun unresolvedToggleSummary(mode: NetworkControlMode): String =
        "无法解析当前${labelFor(mode)}状态，已取消切换"

    fun writeFailedSummary(mode: NetworkControlMode): String =
        "${labelFor(mode)}写入失败"

    fun withMode(
        mode: NetworkControlMode,
        result: ExecutorCommandResult,
        summary: String = result.summary,
    ): ExecutorCommandResult = result.copy(
        controlMode = mode,
        controlledEnabled = parseBinaryToggleState(result.stdout),
        summary = summary,
    )
}
