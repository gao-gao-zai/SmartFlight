package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.executor.ExecutorCommand
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.executor.ExecutorProbeService
import javax.inject.Inject

interface NetworkControlProbe {
    suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult

    suspend fun probeNetworkControlState(mode: NetworkControlMode): ExecutorCommandResult

    suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult

    suspend fun setNetworkControlEnabled(
        mode: NetworkControlMode,
        enabled: Boolean,
        knownCurrentEnabled: Boolean? = null,
    ): ExecutorCommandResult

    suspend fun toggleCurrentNetworkControlState(
        knownControlledEnabled: Boolean? = null,
    ): ExecutorCommandResult

    suspend fun setDisconnectedState(
        disconnected: Boolean,
        knownDisconnected: Boolean? = null,
    ): ExecutorCommandResult
}

class ExecutorNetworkControlProbe @Inject constructor(
    private val executorProbeService: ExecutorProbeService,
) : NetworkControlProbe {
    override suspend fun runCommand(command: ExecutorCommand): ExecutorCommandResult =
        executorProbeService.runCommand(command)

    override suspend fun probeNetworkControlState(mode: NetworkControlMode): ExecutorCommandResult =
        executorProbeService.probeNetworkControlState(mode)

    override suspend fun probeCurrentNetworkControlState(): ExecutorCommandResult =
        executorProbeService.probeCurrentNetworkControlState()

    override suspend fun setNetworkControlEnabled(
        mode: NetworkControlMode,
        enabled: Boolean,
        knownCurrentEnabled: Boolean?,
    ): ExecutorCommandResult =
        executorProbeService.setNetworkControlEnabled(
            mode = mode,
            enabled = enabled,
            knownCurrentEnabled = knownCurrentEnabled,
        )

    override suspend fun toggleCurrentNetworkControlState(
        knownControlledEnabled: Boolean?,
    ): ExecutorCommandResult =
        executorProbeService.toggleCurrentNetworkControlState(
            knownControlledEnabled = knownControlledEnabled,
        )

    override suspend fun setDisconnectedState(
        disconnected: Boolean,
        knownDisconnected: Boolean?,
    ): ExecutorCommandResult =
        executorProbeService.setDisconnectedState(
            disconnected = disconnected,
            knownDisconnected = knownDisconnected,
        )
}
