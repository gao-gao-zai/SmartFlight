package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedAccessChecker @Inject constructor(
    private val rootAccessChecker: RootAccessChecker,
    private val shizukuAccessChecker: ShizukuAccessChecker,
    private val adbAccessChecker: AdbAccessChecker,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun check(): AdvancedAccessState {
        val root = rootAccessChecker.check()
        val shizuku = shizukuAccessChecker.check()
        val adbBootstrapped = adbAccessChecker.check()
        val checks = listOf(root, shizuku, adbBootstrapped)
        val preferredExecutorType = settingsRepository.settings.first().preferredExecutorType
        val availableExecutors = buildList {
            if (shizuku.satisfiesRequirement) add(ExecutorType.Shizuku)
            if (root.satisfiesRequirement) add(ExecutorType.Root)
            if (adbBootstrapped.satisfiesRequirement) add(ExecutorType.AdbBootstrapped)
        }
        return AdvancedAccessState(
            selectedExecutorType = when {
                preferredExecutorType != ExecutorType.Auto && preferredExecutorType in availableExecutors ->
                    preferredExecutorType
                ExecutorType.Shizuku in availableExecutors -> ExecutorType.Shizuku
                ExecutorType.Root in availableExecutors -> ExecutorType.Root
                ExecutorType.AdbBootstrapped in availableExecutors -> ExecutorType.AdbBootstrapped
                else -> ExecutorType.Unavailable
            },
            checks = checks,
        )
    }
}
