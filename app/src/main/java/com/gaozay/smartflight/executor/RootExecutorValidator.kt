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
        if (!snapshot.confirmedAvailable) {
            return ExecutorValidationResult(
                executorType = ExecutorType.Root,
                isReady = false,
                summary = "Root 执行器待确认授权",
                detail = if (snapshot.lastProbeAtMillis > 0) {
                    snapshot.lastProbeSummary
                } else {
                    "尚未执行 Root 主动授权测试"
                },
                command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
            )
        }

        return ExecutorValidationResult(
            executorType = ExecutorType.Root,
            isReady = true,
            summary = "Root 执行器已确认授权",
            detail = snapshot.lastProbeSummary.ifBlank { "已完成 Root 主动授权测试" },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
        )
    }
}
