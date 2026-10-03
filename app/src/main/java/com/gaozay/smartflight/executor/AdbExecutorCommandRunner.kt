package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbExecutorCommandRunner @Inject constructor() : ExecutorCommandRunner {
    override suspend fun run(command: ExecutorCommand): ExecutorCommandResult =
        ExecutorCommandResult(
            executorType = ExecutorType.AdbBootstrapped,
            executed = false,
            summary = AppStrings.get(R.string.adb_command_executor_is_not_integrated_yet),
            stderr = AppStrings.get(R.string.adb_execution_path_pending),
        )
}
