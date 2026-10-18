package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

@Singleton
class ExecutorProbeService internal constructor(
    private val settingsRepository: SettingsRepository,
    private val selectRunner: suspend () -> SelectedExecutorRunner,
    private val commandMapper: NetworkControlCommandMapper,
    private val mobileDataSupportChecker: MobileDataSupportChecker,
) {
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        executorRunnerSelector: ExecutorRunnerSelector,
        commandMapper: NetworkControlCommandMapper,
        mobileDataSupportChecker: MobileDataSupportChecker,
    ) : this(settingsRepository, executorRunnerSelector::currentRunner, commandMapper, mobileDataSupportChecker)

    private val changeMutex = Mutex()

    private suspend fun currentMode(): NetworkControlMode = settingsRepository.settings.first().networkControlMode

    suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult {
        val selected = selectRunner()
        return selected.runner?.let { runBounded(selected.executorType, it, command) } ?: ExecutorCommandResult(
            executorType = selected.executorType,
            executed = false,
            summary = AppStrings.get(R.string.no_executor_available_for, command.purpose),
        )
    }

    suspend fun probeNetworkControlState(mode: NetworkControlMode): ExecutorCommandResult {
        val selected = selectRunner()
        val runner = selected.runner
        if (mode == NetworkControlMode.MobileData && runner != null) {
            mobileDataSupportChecker.unsupportedResultOrNull(selected.executorType, boundedRunner(selected.executorType, runner))?.let { return it }
        }
        val result = runner?.let { runBounded(selected.executorType, it, commandMapper.readCommandFor(mode)) } ?: ExecutorCommandResult(
            executorType = selected.executorType,
            executed = false,
            summary = commandMapper.noReadExecutorSummary(mode),
        )
        return observedState(mode, result)
    }

    suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult = probeNetworkControlState(currentMode())

    // Snapshot hints have no freshness guarantee. Always read inside the change lock.
    @Suppress("UNUSED_PARAMETER")
    suspend fun setNetworkControlEnabled(
        mode: NetworkControlMode,
        enabled: Boolean,
        knownCurrentEnabled: Boolean? = null,
    ): ExecutorCommandResult = changeMutex.withLock {
        changeNetworkControl(mode) { enabled }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun toggleCurrentNetworkControlState(
        knownControlledEnabled: Boolean? = null,
    ): ExecutorCommandResult = changeMutex.withLock {
        changeNetworkControl(currentMode()) { !it }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun setDisconnectedState(
        disconnected: Boolean,
        knownDisconnected: Boolean? = null,
    ): ExecutorCommandResult = changeMutex.withLock {
        val mode = currentMode()
        changeNetworkControl(mode) {
            if (mode == NetworkControlMode.AirplaneMode) disconnected else !disconnected
        }
    }

    private suspend fun changeNetworkControl(
        mode: NetworkControlMode,
        target: (Boolean) -> Boolean,
    ): ExecutorCommandResult {
        val selected = selectRunner()
        val runner = selected.runner?.let { boundedRunner(selected.executorType, it) } ?: return ExecutorCommandResult(
            executorType = ExecutorType.Unavailable,
            controlMode = mode,
            executed = false,
            summary = commandMapper.noToggleExecutorSummary(mode),
        )
        if (mode == NetworkControlMode.MobileData) {
            mobileDataSupportChecker.unsupportedResultOrNull(selected.executorType, runner)?.let { return it }
        }
        val initial = observedState(mode, runner.run(commandMapper.readCommandFor(mode)))
        val currentEnabled = initial.controlledEnabled ?: return initial.copy(
            executed = false,
            summary = commandMapper.unresolvedSetSummary(mode),
        )
        val enabled = target(currentEnabled)
        if (currentEnabled == enabled) {
            return initial.copy(
                executed = false,
                summary = commandMapper.alreadyInStateSummary(mode, enabled),
                alreadyInRequestedState = true,
                requestedEnabled = enabled,
            )
        }

        val writeResult = runner.run(commandMapper.writeCommandFor(mode, enabled))
        // Exit status describes the shell. Only a fresh read describes the resulting state.
        var readback: ExecutorCommandResult? = null
        // Android may apply a radio setting asynchronously. Confirm for a short, bounded window.
        withTimeoutOrNull(1_000) {
            repeat(4) { attempt ->
                readback = observedState(mode, runner.run(commandMapper.readCommandFor(mode)))
                if (readback?.controlledEnabled == enabled) return@withTimeoutOrNull
                if (attempt < 3) delay(150)
            }
        }
        return writeResult.copy(
            controlMode = mode,
            controlledEnabled = readback?.controlledEnabled,
            requestedEnabled = enabled,
            stderr = listOf(writeResult.stderr, readback?.stderr.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"),
            summary = if (readback?.controlledEnabled == enabled) {
                commandMapper.enabledChangedSummary(mode, enabled)
            } else {
                commandMapper.writeFailedSummary(mode)
            },
        )
    }

    private fun observedState(mode: NetworkControlMode, result: ExecutorCommandResult): ExecutorCommandResult =
        commandMapper.withMode(mode, result).let {
            if (result.executed && result.exitCode == 0) it else it.copy(controlledEnabled = null)
        }

    private fun boundedRunner(type: ExecutorType, runner: ExecutorCommandRunner): ExecutorCommandRunner =
        object : ExecutorCommandRunner {
            override suspend fun run(command: ExecutorCommand): ExecutorCommandResult = runBounded(type, runner, command)
        }

    private suspend fun runBounded(
        type: ExecutorType,
        runner: ExecutorCommandRunner,
        command: ExecutorCommand,
    ): ExecutorCommandResult = withTimeoutOrNull(6_000) { runner.run(command) } ?: ExecutorCommandResult(
        executorType = type,
        executed = false,
        summary = AppStrings.get(R.string.executor_command_timeout, command.purpose),
    )
}
