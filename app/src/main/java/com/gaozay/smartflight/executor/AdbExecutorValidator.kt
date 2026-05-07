package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.permission.AdbBootstrapRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbExecutorValidator @Inject constructor(
    private val adbBootstrapRepository: AdbBootstrapRepository,
) : ExecutorValidator {
    override suspend fun validate(): ExecutorValidationResult {
        val bootstrapped = adbBootstrapRepository.getSnapshot().bootstrapped
        return ExecutorValidationResult(
            executorType = ExecutorType.AdbBootstrapped,
            isReady = bootstrapped,
            summary = if (bootstrapped) {
                "ADB 执行器具备读取飞行模式状态的初始化前提"
            } else {
                "ADB 执行器尚未初始化"
            },
            detail = if (bootstrapped) {
                "初始化状态已记录，但真实的设备侧命令执行链路还未接入。"
            } else {
                "需要先完成 ADB 初始化，执行器才会进入可用候选。"
            },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
        )
    }
}
