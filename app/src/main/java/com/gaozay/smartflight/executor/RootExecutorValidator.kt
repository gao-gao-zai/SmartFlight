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
            ExecutorCommand(
                rawCommand = "id",
                purpose = "验证 Root shell 是否可执行",
            ),
        )
        val confirmed = commandResult.executed &&
            commandResult.exitCode == 0 &&
            commandResult.stdout.contains("uid=0")
        return ExecutorValidationResult(
            executorType = ExecutorType.Root,
            isReady = confirmed,
            summary = if (confirmed) {
                "Root 执行器已通过只读命令验证"
            } else {
                "Root 执行器尚未通过只读命令验证"
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
        )
    }
}
