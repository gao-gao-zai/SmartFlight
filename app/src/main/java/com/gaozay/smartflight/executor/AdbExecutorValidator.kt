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
                "ADB 执行器具备初始化前提"
            } else {
                "ADB 执行器尚未初始化"
            },
            detail = if (bootstrapped) {
                "当前仍缺少真实执行命令的联调，但初始化状态已记录。"
            } else {
                "需要先完成 ADB 初始化，执行器才会进入可用候选。"
            },
        )
    }
}
