package com.gaozay.smartflight.shizuku

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

class ShizukuCommandService() : IShizukuCommandService.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun runReadonlyCommand(command: String): String {
        val shell = findShellPath()
        val process = ProcessBuilder(shell, "-c", command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!finished) {
            process.destroy()
            return "timeout"
        }
        return buildString {
            append("exit=")
            append(process.exitValue())
            if (output.isNotBlank()) {
                append('\n')
                append(output)
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
