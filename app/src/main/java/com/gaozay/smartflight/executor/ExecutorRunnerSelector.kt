package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SelectedExecutorRunner(
    val executorType: ExecutorType,
    val runner: ExecutorCommandRunner?,
)

@Singleton
class ExecutorRunnerSelector @Inject constructor(
    private val executorValidationService: ExecutorValidationService,
    private val settingsRepository: SettingsRepository,
    private val rootExecutorCommandRunner: RootExecutorCommandRunner,
    private val shizukuExecutorCommandRunner: ShizukuExecutorCommandRunner,
    private val adbExecutorCommandRunner: AdbExecutorCommandRunner,
) {
    suspend fun currentRunner(): SelectedExecutorRunner {
        val validations = executorValidationService.validateAll()
        val preferredExecutorType = settingsRepository.settings.first().preferredExecutorType
        val selected = executorValidationService.selectPreferredExecutor(validations, preferredExecutorType)
        val runner = when (selected.executorType) {
            ExecutorType.Root -> rootExecutorCommandRunner
            ExecutorType.Shizuku -> shizukuExecutorCommandRunner
            ExecutorType.AdbBootstrapped -> adbExecutorCommandRunner
            else -> null
        }
        return SelectedExecutorRunner(
            executorType = selected.executorType,
            runner = runner,
        )
    }
}
