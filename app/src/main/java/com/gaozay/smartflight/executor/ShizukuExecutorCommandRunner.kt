package com.gaozay.smartflight.executor

import android.content.pm.PackageManager
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.shizuku.ShizukuServiceManager
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

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
                summary = "Shizuku 命令未执行：Binder 未连接",
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = "Shizuku 命令未执行：缺少授权",
            )
        }

        val service = shizukuServiceManager.getOrBindService()
            ?: return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = "Shizuku 命令未执行：无法绑定 UserService",
            )

        val rawResult = runCatching {
            service.runReadonlyCommand(command.rawCommand)
        }.getOrElse { throwable ->
            return ExecutorCommandResult(
                executorType = ExecutorType.Shizuku,
                executed = false,
                summary = "Shizuku 命令执行异常",
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
                "Shizuku 命令执行成功"
            } else {
                "Shizuku 命令执行失败"
            },
        )
    }
}
