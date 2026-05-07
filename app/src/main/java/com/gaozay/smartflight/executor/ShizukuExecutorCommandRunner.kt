package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuExecutorCommandRunner @Inject constructor() : ExecutorCommandRunner {
    override suspend fun run(command: ExecutorCommand): ExecutorCommandResult =
        ExecutorCommandResult(
            executorType = ExecutorType.Shizuku,
            executed = false,
            summary = "Shizuku 命令执行器尚未接入",
            stderr = "当前只完成 Binder 和授权检测，后续应通过 UserService 或受控 shell 方案执行命令。",
        )
}
