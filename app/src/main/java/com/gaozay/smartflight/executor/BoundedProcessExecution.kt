package com.gaozay.smartflight.executor

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Drains output while waiting, including the case where a descendant keeps stdout open. */
internal object BoundedProcessExecution {
    data class Result(val exitCode: Int?, val output: String, val timedOut: Boolean)

    fun await(process: Process, timeoutMillis: Long): Result {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val output = daemonTask("SmartFlight-process-output") {
            val text = StringBuilder()
            val buffer = CharArray(2048)
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    val remaining = MAX_OUTPUT_CHARS - text.length
                    if (remaining > 0) text.append(buffer, 0, minOf(count, remaining))
                }
            }
            text.toString().trim()
        }
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return Result(null, "", timedOut = true)
            }
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(1)
            return try {
                Result(process.exitValue(), output.get(remaining, TimeUnit.NANOSECONDS), timedOut = false)
            } catch (_: TimeoutException) {
                Result(null, "", timedOut = true)
            }
        } finally {
            terminate(process)
            output.cancel(true)
        }
    }

    // Stream close can take a lock held by a blocked read. Never do that on the deadline path.
    fun terminate(process: Process) {
        runCatching { process.destroy() }
        runCatching { if (process.isAlive) process.destroyForcibly() }
        daemonTask("SmartFlight-process-cleanup") {
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    fun <T> daemonTask(name: String, block: () -> T): FutureTask<T> = FutureTask(block).also {
        Thread(it, name).apply { isDaemon = true }.start()
    }

    private const val MAX_OUTPUT_CHARS = 64 * 1024
}
