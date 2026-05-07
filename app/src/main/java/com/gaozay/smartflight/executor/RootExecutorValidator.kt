package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootExecutorValidator @Inject constructor(
    private val rootExecutorCommandRunner: RootExecutorCommandRunner,
) : ExecutorValidator {
    override suspend fun validate(): ExecutorValidationResult {
        val commandResult = rootExecutorCommandRunner.run(
            ExecutorReadonlyCommands.ReadAirplaneModeState,
        )
        val confirmed = commandResult.executed &&
            commandResult.exitCode == 0 &&
            (commandResult.stdout == "0" || commandResult.stdout == "1")
        return ExecutorValidationResult(
            executorType = ExecutorType.Root,
            isReady = confirmed,
            summary = if (confirmed) {
                "Root 执行器已读取飞行模式状态"
            } else {
                "Root 执行器未能读取飞行模式状态"
            },
            detail = buildString {
                if (commandResult.stdout.isNotBlank()) {
                    append(commandResult.stdout.lineSequence().first())
                }
                if (commandResult.stderr.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(commandResult.stderr.lineSequence().first())
                }
                if (isEmpty()) {
                    append(commandResult.summary)
                }
            },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
            commandOutput = commandResult.stdout.ifBlank { commandResult.stderr.ifBlank { null } },
        )
    }
}
