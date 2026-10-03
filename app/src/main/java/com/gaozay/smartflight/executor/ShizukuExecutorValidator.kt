package com.gaozay.smartflight.executor

import android.content.pm.PackageManager
import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject
import javax.inject.Singleton
import rikka.shizuku.Shizuku

@Singleton
class ShizukuExecutorValidator @Inject constructor(
    private val shizukuExecutorCommandRunner: ShizukuExecutorCommandRunner,
) : ExecutorValidator {
    override suspend fun validate(): ExecutorValidationResult {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ExecutorValidationResult(
                executorType = ExecutorType.Shizuku,
                isReady = false,
                summary = AppStrings.get(R.string.shizuku_executor_is_not_ready),
                detail = AppStrings.get(R.string.binder_is_not_connected_yet_the_shizuku_service_cannot_be_called),
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val remoteUid = runCatching { Shizuku.getUid() }.getOrNull()
        if (!granted) {
            return ExecutorValidationResult(
                executorType = ExecutorType.Shizuku,
                isReady = false,
                summary = AppStrings.get(R.string.shizuku_executor_permission_not_granted),
                detail = buildString {
                    append(AppStrings.get(R.string.binder_connected))
                    if (remoteUid != null) {
                        append(AppStrings.get(R.string.backend_uid))
                        append(remoteUid)
                    }
                },
            )
        }

        val commandResult = shizukuExecutorCommandRunner.run(
            ExecutorReadonlyCommands.ReadAirplaneModeState,
        )

        return ExecutorValidationResult(
            executorType = ExecutorType.Shizuku,
            isReady = commandResult.executed &&
                commandResult.exitCode == 0 &&
                (commandResult.stdout == "0" || commandResult.stdout == "1"),
            summary = if (commandResult.executed && commandResult.exitCode == 0 &&
                (commandResult.stdout == "0" || commandResult.stdout == "1")
            ) {
                AppStrings.get(R.string.shizuku_executor_read_the_airplane_mode_state)
            } else {
                AppStrings.get(R.string.shizuku_executor_could_not_read_the_airplane_mode_state)
            },
            detail = buildString {
                append(commandResult.stdout.ifBlank { commandResult.summary })
                if (remoteUid != null) {
                    append(AppStrings.get(R.string.backend_uid_shizuku_executor_validator))
                    append(remoteUid)
                    append(if (remoteUid == 0) AppStrings.get(R.string.root_uid_suffix) else if (remoteUid == 2000) AppStrings.get(R.string.adb_uid_suffix) else "")
                }
            },
            command = ExecutorReadonlyCommands.ReadAirplaneModeState.rawCommand,
            commandOutput = commandResult.stdout.ifBlank { commandResult.stderr.ifBlank { null } },
        )
    }
}
