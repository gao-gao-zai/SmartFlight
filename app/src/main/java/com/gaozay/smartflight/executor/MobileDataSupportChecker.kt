package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileDataSupportChecker @Inject constructor() {
    suspend fun unsupportedResultOrNull(
        executorType: ExecutorType,
        runner: ExecutorCommandRunner,
    ): ExecutorCommandResult? {
        val checkResult = runner.run(ExecutorReadonlyCommands.CheckPhoneService)
        return if (isPhoneServiceUnavailable(checkResult.stdout, checkResult.stderr)) {
            ExecutorCommandResult(
                executorType = executorType,
                controlMode = NetworkControlMode.MobileData,
                executed = false,
                exitCode = checkResult.exitCode,
                stdout = checkResult.stdout,
                stderr = checkResult.stderr,
                summary = MOBILE_DATA_UNSUPPORTED_SUMMARY,
            )
        } else {
            null
        }
    }

    companion object {
        const val MOBILE_DATA_UNSUPPORTED_SUMMARY = AppStrings.get(R.string.mobile_data_unsupported_recommendation)
    }
}
