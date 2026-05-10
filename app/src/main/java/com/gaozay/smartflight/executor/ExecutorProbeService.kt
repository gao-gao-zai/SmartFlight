package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutorProbeService @Inject constructor(
    private val executorValidationService: ExecutorValidationService,
    private val settingsRepository: SettingsRepository,
    private val rootExecutorCommandRunner: RootExecutorCommandRunner,
    private val shizukuExecutorCommandRunner: ShizukuExecutorCommandRunner,
    private val adbExecutorCommandRunner: AdbExecutorCommandRunner,
) {
    private val mobileDataUnsupportedSummary = "当前设备不支持移动数据切换，请改用飞行模式"

    private suspend fun currentRunner(): Pair<ExecutorType, ExecutorCommandRunner?> {
        val validations = executorValidationService.validateAll()
        val preferredExecutorType = settingsRepository.settings.first().preferredExecutorType
        val selected = executorValidationService.selectPreferredExecutor(validations, preferredExecutorType)
        val runner = when (selected.executorType) {
            ExecutorType.Root -> rootExecutorCommandRunner
            ExecutorType.Shizuku -> shizukuExecutorCommandRunner
            ExecutorType.AdbBootstrapped -> adbExecutorCommandRunner
            else -> null
        }
        return selected.executorType to runner
    }

    private suspend fun currentMode(): NetworkControlMode = settingsRepository.settings.first().networkControlMode

    private fun readCommandFor(mode: NetworkControlMode): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorReadonlyCommands.ReadAirplaneModeState
            NetworkControlMode.MobileData -> ExecutorReadonlyCommands.ReadMobileDataState
        }

    private fun writeCommandFor(mode: NetworkControlMode, enabled: Boolean): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorWriteCommands.setAirplaneModeState(enabled)
            NetworkControlMode.MobileData -> ExecutorWriteCommands.setMobileDataEnabled(enabled)
        }

    private fun labelFor(mode: NetworkControlMode): String =
        when (mode) {
            NetworkControlMode.AirplaneMode -> "飞行模式"
            NetworkControlMode.MobileData -> "移动数据"
        }

    private suspend fun ensureMobileDataSupported(
        executorType: ExecutorType,
        runner: ExecutorCommandRunner,
    ): ExecutorCommandResult? {
        val checkResult = runner.run(ExecutorReadonlyCommands.CheckPhoneService)
        return if (isPhoneServiceUnavailable(checkResult.stdout, checkResult.stderr)) {
            ExecutorCommandResult(
                executorType = executorType,
                controlMode = NetworkControlMode.MobileData,
                executed = false,
                exitCode = checkResult.exitCode,
                stdout = checkResult.stdout,
                stderr = checkResult.stderr,
                summary = mobileDataUnsupportedSummary,
            )
        } else {
            null
        }
    }

    private fun withMode(
        mode: NetworkControlMode,
        result: ExecutorCommandResult,
        summary: String = result.summary,
    ): ExecutorCommandResult = result.copy(
        controlMode = mode,
        controlledEnabled = parseBinaryToggleState(result.stdout),
        summary = summary,
    )

    suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult {
        val (executorType, runner) = currentRunner()
        return runner?.run(command) ?: ExecutorCommandResult(
            executorType = executorType,
            executed = false,
            summary = "没有可用于${command.purpose}的执行器",
        )
    }

    suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult {
        val mode = currentMode()
        val (executorType, runner) = currentRunner()
        if (mode == NetworkControlMode.MobileData && runner != null) {
            ensureMobileDataSupported(executorType, runner)?.let { return it }
        }
        return runCommand(readCommandFor(mode)).let { result ->
            if (!result.executed && result.summary.startsWith("没有可用于")) {
                withMode(mode, result, "没有可用于读取${labelFor(mode)}状态的执行器")
            } else {
                withMode(mode, result)
            }
        }
    }

    suspend fun toggleCurrentNetworkControlState(): ExecutorCommandResult {
        val mode = currentMode()
        val (executorType, runner) = currentRunner()
        if (runner == null) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                controlMode = mode,
                executed = false,
                summary = "没有可用于切换${labelFor(mode)}的执行器",
            )
        }
        if (mode == NetworkControlMode.MobileData) {
            ensureMobileDataSupported(executorType, runner)?.let { return it }
        }

        val currentState = withMode(mode, runner.run(readCommandFor(mode)))
        val currentEnabled = currentState.controlledEnabled
        if (currentEnabled == null) {
            return currentState.copy(
                executed = false,
                summary = "无法解析当前${labelFor(mode)}状态，已取消切换",
            )
        }

        val targetEnabled = !currentEnabled
        val writeResult = withMode(mode, runner.run(writeCommandFor(mode, targetEnabled)))
        if (!writeResult.executed || writeResult.exitCode != 0) {
            return writeResult.copy(
                summary = "${labelFor(mode)}写入失败",
            )
        }

        val verification = withMode(mode, runner.run(readCommandFor(mode)))
        val finalEnabled = verification.controlledEnabled
        if (finalEnabled == targetEnabled) {
            return verification.copy(
                summary = when (mode) {
                    NetworkControlMode.AirplaneMode ->
                        if (targetEnabled) "飞行模式已开启" else "飞行模式已关闭"
                    NetworkControlMode.MobileData ->
                        if (targetEnabled) "移动数据已开启" else "移动数据已关闭"
                },
            )
        }

        return verification.copy(
            summary = "${labelFor(mode)}写入后校验异常",
        )
    }

    suspend fun setDisconnectedState(disconnected: Boolean): ExecutorCommandResult {
        val mode = currentMode()
        val (executorType, runner) = currentRunner()
        if (runner == null) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                controlMode = mode,
                executed = false,
                summary = "没有可用于设置${labelFor(mode)}的执行器",
            )
        }
        if (mode == NetworkControlMode.MobileData) {
            ensureMobileDataSupported(executorType, runner)?.let { return it }
        }

        val currentState = withMode(mode, runner.run(readCommandFor(mode)))
        val currentEnabled = currentState.controlledEnabled
        if (currentEnabled == null) {
            return currentState.copy(
                executed = false,
                summary = "无法解析当前${labelFor(mode)}状态，已取消设置",
            )
        }

        val targetEnabled = when (mode) {
            NetworkControlMode.AirplaneMode -> disconnected
            NetworkControlMode.MobileData -> !disconnected
        }
        if (currentEnabled == targetEnabled) {
            return currentState.copy(
                summary = when (mode) {
                    NetworkControlMode.AirplaneMode ->
                        if (targetEnabled) "飞行模式已处于开启状态" else "飞行模式已处于关闭状态"
                    NetworkControlMode.MobileData ->
                        if (targetEnabled) "移动数据已处于开启状态" else "移动数据已处于关闭状态"
                },
            )
        }

        val writeResult = withMode(mode, runner.run(writeCommandFor(mode, targetEnabled)))
        if (!writeResult.executed || writeResult.exitCode != 0) {
            return writeResult.copy(summary = "${labelFor(mode)}写入失败")
        }

        val verification = withMode(mode, runner.run(readCommandFor(mode)))
        val finalEnabled = verification.controlledEnabled
        if (finalEnabled == targetEnabled) {
            return verification.copy(
                summary = when (mode) {
                    NetworkControlMode.AirplaneMode ->
                        if (targetEnabled) "飞行模式已开启" else "飞行模式已关闭"
                    NetworkControlMode.MobileData ->
                        if (targetEnabled) "移动数据已开启" else "移动数据已关闭"
                },
            )
        }

        return verification.copy(
            summary = "${labelFor(mode)}写入后校验异常",
        )
    }
}
