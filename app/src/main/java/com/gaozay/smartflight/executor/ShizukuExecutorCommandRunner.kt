package com.gaozay.smartflight.executor

import android.content.pm.PackageManager
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.shizuku.ShizukuServiceManager
import javax.inject.Inject
import javax.inject.Singleton
import rikka.shizuku.Shizuku

@Singleton
class ShizukuExecutorCommandRunner @Inject constructor(
    private val shizukuServiceManager: ShizukuServiceManager,
) : ExecutorCommandRunner {
    override suspend fun run(command: ExecutorCommand): ExecutorCommandResult {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = AppStrings.get(R.string.shizuku_command_not_executed_binder_not_connected),
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = AppStrings.get(R.string.shizuku_command_not_executed_permission_not_granted),
            )
        }

        val service = shizukuServiceManager.getOrBindService()
            ?: return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = AppStrings.get(R.string.shizuku_command_not_executed_unable_to_bind_userservice),
            )

        val rawResult = runCatching {
            service.runCommand(command.rawCommand)
        }.getOrElse { throwable ->
            return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = AppStrings.get(R.string.shizuku_command_execution_error),
                stderr = throwable.message.orEmpty(),
            )
        }

        val lines = rawResult.lineSequence().toList()
        val exitCode = lines.firstOrNull()
            ?.removePrefix("exit=")
            ?.toIntOrNull()
        val stdout = lines.drop(1).joinToString("\n").trim()

        return ExecutorCommandResult(
            executorType = ExecutorType.Shizuku,
            executed = exitCode != null,
            exitCode = exitCode,
            stdout = stdout,
            summary = if (exitCode == 0) {
                AppStrings.get(R.string.shizuku_command_executed_successfully)
            } else {
                AppStrings.get(R.string.shizuku_command_execution_failed)
            },
        )
    }
}
