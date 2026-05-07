package com.gaozay.smartflight.permission

import com.gaozay.smartflight.executor.ExecutorValidationService
import com.gaozay.smartflight.domain.model.ExecutorType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedAccessChecker @Inject constructor(
    private val rootAccessChecker: RootAccessChecker,
    private val shizukuAccessChecker: ShizukuAccessChecker,
    private val adbAccessChecker: AdbAccessChecker,
    private val executorValidationService: ExecutorValidationService,
) {
    suspend fun check(): AdvancedAccessState {
        val root = rootAccessChecker.check()
        val shizuku = shizukuAccessChecker.check()
        val adbBootstrapped = adbAccessChecker.check()
        val checks = listOf(root, shizuku, adbBootstrapped)
        val selectedExecutorType = executorValidationService.selectBestExecutor().executorType
        return AdvancedAccessState(
            selectedExecutorType = selectedExecutorType,
            checks = checks,
        )
    }
}
