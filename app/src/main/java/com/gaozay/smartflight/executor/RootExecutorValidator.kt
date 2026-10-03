package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
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
                summary = AppStrings.get(R.string.root_executor_authorization_is_unconfirmed),
                detail = if (snapshot.lastProbeAtMillis > 0) {
                    snapshot.lastProbeSummary
                } else {
                    AppStrings.get(R.string.no_active_root_authorization_test_has_been_run_yet)
                },
                command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
            )
        }

        return ExecutorValidationResult(
            executorType = ExecutorType.Root,
            isReady = true,
            summary = AppStrings.get(R.string.root_executor_authorization_confirmed),
            detail = snapshot.lastProbeSummary.ifBlank { AppStrings.get(R.string.active_root_authorization_test_completed) },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
        )
    }
}
