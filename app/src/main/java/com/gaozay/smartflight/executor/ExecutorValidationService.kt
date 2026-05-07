package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutorValidationService @Inject constructor(
    private val shizukuExecutorValidator: ShizukuExecutorValidator,
    private val rootExecutorValidator: RootExecutorValidator,
    private val adbExecutorValidator: AdbExecutorValidator,
) {
    suspend fun validateAll(): List<ExecutorValidationResult> = listOf(
        shizukuExecutorValidator.validate(),
        rootExecutorValidator.validate(),
        adbExecutorValidator.validate(),
    )

    suspend fun selectBestExecutor(): ExecutorValidationResult {
        val results = validateAll()
        return selectBestExecutor(results)
    }

    fun selectBestExecutor(results: List<ExecutorValidationResult>): ExecutorValidationResult {
        return results.firstOrNull { it.isReady }
            ?: ExecutorValidationResult(
                executorType = ExecutorType.Unavailable,
                isReady = false,
                summary = "尚无可用执行器",
                detail = results.joinToString(separator = "；") { it.summary },
            )
    }
}
