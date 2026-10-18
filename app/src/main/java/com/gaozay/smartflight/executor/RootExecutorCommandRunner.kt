package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.i18n.AppStrings
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RootExecutorCommandRunner @Inject constructor() : ExecutorCommandRunner {
    private val sessionMutex = Mutex()
    private var session: RootSession? = null

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
                summary = AppStrings.get(R.string.root_command_not_executed_su_not_found),
            )
        }

        sessionMutex.withLock {
            val activeSession = session?.takeIf { it.isAlive() } ?: createSession(suPath).also { session = it }
            if (activeSession == null) {
                session = null
                return@withLock ExecutorCommandResult(
                    executorType = ExecutorType.Root,
                    executed = false,
                    summary = AppStrings.get(R.string.root_command_not_executed_unable_to_start_the_su_process),
                )
            }
            runCatching {
                activeSession.execute(command)
            }.getOrElse {
                activeSession.close()
                session = null
                ExecutorCommandResult(
                    executorType = ExecutorType.Root,
                    executed = false,
                    summary = AppStrings.get(R.string.root_command_execution_error),
                    stderr = it.message.orEmpty(),
                )
            }
        }
    }

    private fun createSession(suPath: String): RootSession? {
        return runCatching {
            val process = ProcessBuilder(suPath)
                .redirectErrorStream(true)
                .start()
            RootSession(process)
        }.getOrNull()
    }
}

internal class RootSession(
    private val process: Process,
) {
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
    private val reader = BufferedReader(InputStreamReader(process.inputStream))
    private var commandIndex = 0

    fun isAlive(): Boolean = process.isAlive

    fun execute(command: ExecutorCommand, timeoutMillis: Long = 5_000): ExecutorCommandResult {
        val task = BoundedProcessExecution.daemonTask("SmartFlight-root-command") { executeBlocking(command) }
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            close()
            return ExecutorCommandResult(
                executorType = ExecutorType.Root,
                executed = false,
                summary = AppStrings.get(R.string.root_command_execution_error),
                stderr = "Root command timed out",
            )
        } finally {
            task.cancel(true)
        }
    }

    private fun executeBlocking(command: ExecutorCommand): ExecutorCommandResult {
        val marker = "__SMARTFLIGHT_EXIT_${commandIndex++}__"
        writer.write("${command.rawCommand}\n")
        writer.write("printf \"$marker:%s\\n\" \$?\n")
        writer.flush()

        val outputLines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith("$marker:")) {
                val exitCode = line.substringAfter(':').toIntOrNull()
                val stdout = outputLines.joinToString("\n").trim()
                return ExecutorCommandResult(
                    executorType = ExecutorType.Root,
                    executed = exitCode != null,
                    exitCode = exitCode,
                    stdout = stdout,
                    summary = if (exitCode == 0) {
                        AppStrings.get(R.string.root_command_executed_successfully)
                    } else {
                        AppStrings.get(R.string.root_command_execution_failed)
                    },
                )
            }
            if (outputLines.sumOf { it.length } < 64 * 1024) outputLines += line.take(2048)
        }

        close()
        return ExecutorCommandResult(
            executorType = ExecutorType.Root,
            executed = false,
            stdout = outputLines.joinToString("\n").trim(),
            summary = AppStrings.get(R.string.root_command_not_executed_su_session_closed),
        )
    }

    fun close() {
        BoundedProcessExecution.terminate(process)
    }
}
