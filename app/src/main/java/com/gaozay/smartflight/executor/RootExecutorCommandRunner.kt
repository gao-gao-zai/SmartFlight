package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootExecutorCommandRunner @Inject constructor() : ExecutorCommandRunner {
    override suspend fun run(command: ExecutorCommand): ExecutorCommandResult = withContext(Dispatchers.IO) {
        val suPath = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
        ).firstOrNull { File(it).exists() }

        if (suPath == null) {
            return@withContext ExecutorCommandResult(
                executorType = ExecutorType.Root,
                executed = false,
                summary = "Root 命令未执行：未检测到 su",
            )
        }

        val process = runCatching {
            ProcessBuilder(suPath, "-c", command.rawCommand)
                .redirectErrorStream(false)
                .start()
        }.getOrNull()

        if (process == null) {
            return@withContext ExecutorCommandResult(
                executorType = ExecutorType.Root,
                executed = false,
                summary = "Root 命令未执行：无法启动 su 进程",
            )
        }

        val finished = process.waitFor(2, TimeUnit.SECONDS)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
        if (!finished) {
            process.destroy()
            return@withContext ExecutorCommandResult(
                executorType = ExecutorType.Root,
                executed = false,
                stdout = stdout,
                stderr = stderr,
                summary = "Root 命令执行超时",
            )
        }

        ExecutorCommandResult(
            executorType = ExecutorType.Root,
            executed = true,
            exitCode = process.exitValue(),
            stdout = stdout,
            stderr = stderr,
            summary = if (process.exitValue() == 0) {
                "Root 命令执行成功"
            } else {
                "Root 命令执行失败"
            },
        )
    }
}
