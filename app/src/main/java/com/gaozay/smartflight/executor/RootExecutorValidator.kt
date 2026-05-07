package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.permission.RootAccessProbeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootExecutorValidator @Inject constructor(
    private val rootAccessProbeRepository: RootAccessProbeRepository,
) : ExecutorValidator {
    override suspend fun validate(): ExecutorValidationResult {
        val snapshot = rootAccessProbeRepository.getSnapshot()
        return ExecutorValidationResult(
            executorType = ExecutorType.Root,
            isReady = snapshot.confirmedAvailable,
            summary = if (snapshot.confirmedAvailable) {
                "Root 执行器已通过授权测试"
            } else {
                "Root 执行器尚未通过授权测试"
            },
            detail = snapshot.lastProbeSummary.ifBlank { null },
        )
    }
}
