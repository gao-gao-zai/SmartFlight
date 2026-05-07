package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.executor.ExecutorValidationService
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAccessRepository @Inject constructor(
    private val advancedAccessChecker: AdvancedAccessChecker,
    private val systemPermissionChecker: SystemPermissionChecker,
    private val adbBootstrapRepository: AdbBootstrapRepository,
    private val rootAccessChecker: RootAccessChecker,
    private val executorValidationService: ExecutorValidationService,
    private val runtimeStatusRepository: RuntimeStatusRepository,
) : AccessRepository {
    private val mutableAccessGateState = MutableStateFlow(AccessGateState())

    override val accessGateState: StateFlow<AccessGateState> = mutableAccessGateState.asStateFlow()

    override suspend fun refresh() {
        val advancedAccess = advancedAccessChecker.check()
        val state = AccessGateState(
            advancedAccess = advancedAccess,
            usageStatsAccess = systemPermissionChecker.checkUsageStatsAccess(),
            notificationAccess = systemPermissionChecker.checkNotificationPermission(),
            batteryOptimization = systemPermissionChecker.checkBatteryOptimization(),
            lastCheckedAtMillis = System.currentTimeMillis(),
        )
        mutableAccessGateState.value = state

        val bestExecutor = executorValidationService.selectBestExecutor()
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                activeExecutorType = advancedAccess.selectedExecutorType,
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = if (bestExecutor.isReady) ExecutionResult.Success else ExecutionResult.Pending,
                lastActionReason = buildString {
                    append("执行器自检：")
                    append(bestExecutor.summary)
                    bestExecutor.detail?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setAdbBootstrapped(bootstrapped: Boolean) {
        adbBootstrapRepository.setBootstrapped(bootstrapped)
        refresh()
    }

    override suspend fun probeRootAccess() {
        rootAccessChecker.probeAuthorization()
        refresh()
    }
}
