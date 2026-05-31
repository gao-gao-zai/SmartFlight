package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutorProbeService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val executorRunnerSelector: ExecutorRunnerSelector,
    private val commandMapper: NetworkControlCommandMapper,
    private val mobileDataSupportChecker: MobileDataSupportChecker,
) {
    private suspend fun currentMode(): NetworkControlMode = settingsRepository.settings.first().networkControlMode

    suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult {
        val selected = executorRunnerSelector.currentRunner()
        val executorType = selected.executorType
        val runner = selected.runner
        return runner?.run(command) ?: ExecutorCommandResult(
            executorType = executorType,
            executed = false,
            summary = "没有可用于${command.purpose}的执行器",
        )
    }

    suspend fun probeNetworkControlState(mode: NetworkControlMode): ExecutorCommandResult {
        val selected = executorRunnerSelector.currentRunner()
        val executorType = selected.executorType
        val runner = selected.runner
        if (mode == NetworkControlMode.MobileData && runner != null) {
            mobileDataSupportChecker.unsupportedResultOrNull(executorType, runner)?.let { return it }
        }
        return (runner?.run(commandMapper.readCommandFor(mode)) ?: ExecutorCommandResult(
            executorType = executorType,
            executed = false,
            summary = commandMapper.noReadExecutorSummary(mode),
        )).let { result ->
            if (!result.executed && result.summary.startsWith("没有可用于")) {
                commandMapper.withMode(mode, result, commandMapper.noReadExecutorSummary(mode))
            } else {
                commandMapper.withMode(mode, result)
            }
        }
    }

    suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult = probeNetworkControlState(currentMode())

    suspend fun setNetworkControlEnabled(
        mode: NetworkControlMode,
        enabled: Boolean,
        knownCurrentEnabled: Boolean? = null,
    ): ExecutorCommandResult {
        val selected = executorRunnerSelector.currentRunner()
        val executorType = selected.executorType
        val runner = selected.runner
        if (runner == null) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                controlMode = mode,
                executed = false,
                summary = commandMapper.noToggleExecutorSummary(mode),
            )
        }
        if (mode == NetworkControlMode.MobileData) {
            mobileDataSupportChecker.unsupportedResultOrNull(executorType, runner)?.let { return it }
        }

        val currentEnabled = knownCurrentEnabled ?: commandMapper.withMode(
            mode,
            runner.run(commandMapper.readCommandFor(mode)),
        ).controlledEnabled
        if (currentEnabled == null) {
            return ExecutorCommandResult(
                executorType = executorType,
                controlMode = mode,
                executed = false,
                summary = commandMapper.unresolvedSetSummary(mode),
            )
        }

        if (currentEnabled == enabled) {
            return ExecutorCommandResult(
                executorType = executorType,
                controlMode = mode,
                controlledEnabled = enabled,
                executed = false,
                summary = commandMapper.alreadyInStateSummary(mode, enabled),
            )
        }

        val writeResult = commandMapper.withMode(mode, runner.run(commandMapper.writeCommandFor(mode, enabled)))
        if (!writeResult.executed || writeResult.exitCode != 0) {
            val probedResult = commandMapper.withMode(mode, runner.run(commandMapper.readCommandFor(mode)))
            if (probedResult.controlledEnabled == enabled) {
                return writeResult.copy(
                    controlledEnabled = enabled,
                    summary = commandMapper.enabledChangedSummary(mode, enabled),
                )
            }
            return writeResult.copy(
                summary = commandMapper.writeFailedSummary(mode),
            )
        }

        return writeResult.copy(
            controlledEnabled = enabled,
            summary = commandMapper.enabledChangedSummary(mode, enabled),
        )
    }

    suspend fun toggleCurrentNetworkControlState(
        knownControlledEnabled: Boolean? = null,
    ): ExecutorCommandResult {
        val mode = currentMode()
        val currentEnabled = knownControlledEnabled ?: probeNetworkControlState(mode).controlledEnabled
        if (currentEnabled == null) {
            return ExecutorCommandResult(
                executorType = executorRunnerSelector.currentRunner().executorType,
                controlMode = mode,
                executed = false,
                summary = commandMapper.unresolvedToggleSummary(mode),
            )
        }
        return setNetworkControlEnabled(
            mode = mode,
            enabled = !currentEnabled,
            knownCurrentEnabled = currentEnabled,
        )
    }

    suspend fun setDisconnectedState(
        disconnected: Boolean,
        knownDisconnected: Boolean? = null,
    ): ExecutorCommandResult {
        val mode = currentMode()
        val targetEnabled = when (mode) {
            NetworkControlMode.AirplaneMode -> disconnected
            NetworkControlMode.MobileData -> !disconnected
        }
        val knownCurrentEnabled = knownDisconnected?.let {
            when (mode) {
                NetworkControlMode.AirplaneMode -> it
                NetworkControlMode.MobileData -> !it
            }
        }
        return setNetworkControlEnabled(
            mode = mode,
            enabled = targetEnabled,
            knownCurrentEnabled = knownCurrentEnabled,
        )
    }
}
