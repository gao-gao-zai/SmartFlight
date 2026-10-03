package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
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

    fun selectPreferredExecutor(
        results: List<ExecutorValidationResult>,
        preferredExecutorType: ExecutorType,
    ): ExecutorValidationResult {
        val preferredResult = results.firstOrNull {
            it.executorType == preferredExecutorType && it.isReady
        }
        if (preferredExecutorType != ExecutorType.Auto && preferredResult != null) {
            return preferredResult
        }
        return selectBestExecutor(results)
    }

    fun selectBestExecutor(results: List<ExecutorValidationResult>): ExecutorValidationResult {
        return results.firstOrNull { it.isReady }
            ?: ExecutorValidationResult(
                executorType = ExecutorType.Unavailable,
                isReady = false,
                summary = AppStrings.get(R.string.no_executor_available_yet),
                detail = results.joinToString(separator = AppStrings.get(R.string.summary_separator)) { it.summary },
            )
    }
}
