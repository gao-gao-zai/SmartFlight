package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
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
        const val MOBILE_DATA_UNSUPPORTED_SUMMARY = "当前设备不支持移动数据切换，请改用飞行模式"
    }
}
