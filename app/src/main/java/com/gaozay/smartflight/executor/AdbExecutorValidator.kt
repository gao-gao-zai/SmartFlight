package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
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
                AppStrings.get(R.string.adb_airplane_read_prerequisites_met)
            } else {
                AppStrings.get(R.string.adb_executor_is_not_initialized_yet)
            },
            detail = if (bootstrapped) {
                AppStrings.get(R.string.adb_execution_not_integrated)
            } else {
                AppStrings.get(R.string.complete_adb_initialization_before_the_executor_can_become_a_candidate)
            },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
        )
    }
}
