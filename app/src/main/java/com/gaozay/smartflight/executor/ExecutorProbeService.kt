package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
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
    private fun parseAirplaneModeEnabled(result: ExecutorCommandResult): Boolean? {
        return when (result.stdout.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

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

    suspend fun probeAirplaneModeState(): ExecutorCommandResult {
        val (_, runner) = currentRunner()
        return runner?.run(ExecutorReadonlyCommands.ReadAirplaneModeState)
            ?: ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                executed = false,
                summary = "没有可用于读取飞行模式状态的执行器",
            )
    }

    suspend fun toggleAirplaneModeState(): ExecutorCommandResult {
        val (executorType, runner) = currentRunner()
        if (runner == null) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                executed = false,
                summary = "没有可用于切换飞行模式的执行器",
            )
        }

        val currentState = runner.run(ExecutorReadonlyCommands.ReadAirplaneModeState)
        val currentEnabled = parseAirplaneModeEnabled(currentState)
        if (currentEnabled == null) {
            return ExecutorCommandResult(
                executorType = executorType,
                executed = false,
                exitCode = currentState.exitCode,
                stdout = currentState.stdout,
                stderr = currentState.stderr,
                summary = "无法解析当前飞行模式状态，已取消切换",
            )
        }

        val targetEnabled = !currentEnabled
        val writeResult = runner.run(ExecutorWriteCommands.setAirplaneModeState(targetEnabled))
        if (!writeResult.executed || writeResult.exitCode != 0) {
            return writeResult.copy(
                summary = "飞行模式写入失败",
            )
        }

        val verification = runner.run(ExecutorReadonlyCommands.ReadAirplaneModeState)
        val finalEnabled = parseAirplaneModeEnabled(verification)
        if (finalEnabled == targetEnabled) {
            return verification.copy(
                summary = if (targetEnabled) "飞行模式已开启" else "飞行模式已关闭",
            )
        }

        return verification.copy(
            executed = false,
            summary = "飞行模式写入后校验失败",
        )
    }

    suspend fun setAirplaneModeState(enabled: Boolean): ExecutorCommandResult {
        val (executorType, runner) = currentRunner()
        if (runner == null) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                executed = false,
                summary = "没有可用于设置飞行模式的执行器",
            )
        }

        val currentState = runner.run(ExecutorReadonlyCommands.ReadAirplaneModeState)
        val currentEnabled = parseAirplaneModeEnabled(currentState)
        if (currentEnabled == null) {
            return ExecutorCommandResult(
                executorType = executorType,
                executed = false,
                exitCode = currentState.exitCode,
                stdout = currentState.stdout,
                stderr = currentState.stderr,
                summary = "无法解析当前飞行模式状态，已取消设置",
            )
        }
        if (currentEnabled == enabled) {
            return currentState.copy(
                summary = if (enabled) "飞行模式已处于开启状态" else "飞行模式已处于关闭状态",
            )
        }

        val writeResult = runner.run(ExecutorWriteCommands.setAirplaneModeState(enabled))
        if (!writeResult.executed || writeResult.exitCode != 0) {
            return writeResult.copy(summary = "飞行模式写入失败")
        }

        val verification = runner.run(ExecutorReadonlyCommands.ReadAirplaneModeState)
        val finalEnabled = parseAirplaneModeEnabled(verification)
        if (finalEnabled == enabled) {
            return verification.copy(
                summary = if (enabled) "飞行模式已开启" else "飞行模式已关闭",
            )
        }

        return verification.copy(
            executed = false,
            summary = "飞行模式写入后校验失败",
        )
    }
}
