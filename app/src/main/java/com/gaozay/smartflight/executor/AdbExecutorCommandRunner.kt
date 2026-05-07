package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbExecutorCommandRunner @Inject constructor() : ExecutorCommandRunner {
    override suspend fun run(command: ExecutorCommand): ExecutorCommandResult =
        ExecutorCommandResult(
            executorType = ExecutorType.AdbBootstrapped,
            executed = false,
            summary = "ADB 命令执行器尚未接入",
            stderr = "当前只完成初始化记录，后续需要定义设备侧可执行命令链路。",
        )
}
