package com.gaozay.smartflight.permission

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.executor.ExecutorCommand
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeRuntimeStatusRepository(
    initialSnapshot: RuntimeSnapshot = RuntimeSnapshot(),
) : RuntimeStatusRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)
    override val snapshot: Flow<RuntimeSnapshot> = mutableSnapshot

    override suspend fun updateSnapshot(transform: (RuntimeSnapshot) -> RuntimeSnapshot) {
        mutableSnapshot.value = transform(mutableSnapshot.value)
    }

    val currentSnapshot: RuntimeSnapshot
        get() = mutableSnapshot.value
}

internal class FakeExecutionLogRepository : ExecutionLogRepository {
    val logs = mutableListOf<ExecutionLogEntity>()
    override fun observeRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>> = MutableStateFlow(logs)
    override fun observeLogCount(): Flow<Int> = MutableStateFlow(logs.size)
    override suspend fun addLog(log: ExecutionLogEntity): Long {
        logs += log
        return logs.size.toLong()
    }
    override suspend fun clearLogs() {
        logs.clear()
    }
}

internal class FakeSettingsRepository(
    initialSettings: UserSettings = UserSettings(),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)
    override val settings: Flow<UserSettings> = mutableSettings

    override suspend fun setAutomationEnabled(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(automationEnabled = enabled)
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        mutableSettings.value = transform(mutableSettings.value)
    }
}

internal class FakeNetworkControlProbe : NetworkControlProbe {
    val runCommands = mutableListOf<ExecutorCommand>()
    val setDisconnectedRequests = mutableListOf<Boolean>()
    val setNetworkControlEnabledRequests = mutableListOf<Pair<NetworkControlMode, Boolean>>()
    var currentProbeResult: ExecutorCommandResult = executorResult()
    var networkProbeResults: MutableMap<NetworkControlMode, ArrayDeque<ExecutorCommandResult>> = mutableMapOf()
    var toggleResult: ExecutorCommandResult = executorResult()
    var setDisconnectedResult: ExecutorCommandResult = executorResult()
    var setNetworkControlResults: ArrayDeque<ExecutorCommandResult> = ArrayDeque()
    var runCommandResult: ExecutorCommandResult = executorResult()

    override suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult {
        runCommands += command
        return runCommandResult
    }

    override suspend fun probeNetworkControlState(mode: NetworkControlMode): ExecutorCommandResult =
        networkProbeResults[mode]?.removeFirstOrNull() ?: currentProbeResult.copy(controlMode = mode)

    override suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult = currentProbeResult

    override suspend fun setNetworkControlEnabled(
        mode: NetworkControlMode,
        enabled: Boolean,
        knownCurrentEnabled: Boolean?,
    ): ExecutorCommandResult {
        setNetworkControlEnabledRequests += mode to enabled
        return setNetworkControlResults.removeFirstOrNull()
            ?: executorResult(controlMode = mode, controlledEnabled = enabled)
    }

    override suspend fun toggleCurrentNetworkControlState(
        knownControlledEnabled: Boolean?,
    ): ExecutorCommandResult = toggleResult

    override suspend fun setDisconnectedState(
        disconnected: Boolean,
        knownDisconnected: Boolean?,
    ): ExecutorCommandResult {
        setDisconnectedRequests += disconnected
        return setDisconnectedResult
    }
}

internal fun executorResult(
    controlMode: NetworkControlMode = NetworkControlMode.AirplaneMode,
    controlledEnabled: Boolean? = false,
    executed: Boolean = true,
    exitCode: Int? = 0,
    stdout: String = "",
    stderr: String = "",
    summary: String = "ok",
): ExecutorCommandResult =
    ExecutorCommandResult(
        executorType = com.gaozay.smartflight.domain.model.ExecutorType.Root,
        controlMode = controlMode,
        controlledEnabled = controlledEnabled,
        executed = executed,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        summary = summary,
    )
