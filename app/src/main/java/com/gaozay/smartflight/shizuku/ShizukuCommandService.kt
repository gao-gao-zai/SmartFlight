package com.gaozay.smartflight.shizuku

import android.content.Context
import java.io.File
import com.gaozay.smartflight.executor.BoundedProcessExecution

class ShizukuCommandService() : IShizukuCommandService.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun runCommand(command: String): String {
        val shell = findShellPath()
        val process = ProcessBuilder(shell, "-c", command)
            .redirectErrorStream(true)
            .start()
        val result = BoundedProcessExecution.await(process, timeoutMillis = 2_000)
        if (result.timedOut) return "timeout"
        return buildString {
            append("exit=")
            append(result.exitCode)
            if (result.output.isNotBlank()) {
                append('\n')
                append(result.output)
            }
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun findShellPath(): String = listOf(
        "/system/bin/sh",
        "/system/bin/toybox",
        "/system/bin/cmd",
    ).firstOrNull { File(it).exists() } ?: "sh"
}
