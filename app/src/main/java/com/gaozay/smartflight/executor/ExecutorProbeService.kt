package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutorProbeService @Inject constructor(
    private val executorValidationService: ExecutorValidationService,
    private val rootExecutorCommandRunner: RootExecutorCommandRunner,
    private val shizukuExecutorCommandRunner: ShizukuExecutorCommandRunner,
    private val adbExecutorCommandRunner: AdbExecutorCommandRunner,
) {
    suspend fun probeAirplaneModeState(): ExecutorCommandResult {
        val validations = executorValidationService.validateAll()
        val selected = executorValidationService.selectBestExecutor(validations)
        val command = ExecutorReadonlyCommands.ReadAirplaneModeState
        return when (selected.executorType) {
            ExecutorType.Root -> rootExecutorCommandRunner.run(command)
            ExecutorType.Shizuku -> shizukuExecutorCommandRunner.run(command)
            ExecutorType.AdbBootstrapped -> adbExecutorCommandRunner.run(command)
            else -> ExecutorCommandResult(
                executorType = ExecutorType.Unavailable,
                executed = false,
                summary = "没有可用于读取飞行模式状态的执行器",
            )
        }
    }
}
