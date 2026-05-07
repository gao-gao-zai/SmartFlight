package com.gaozay.smartflight.executor

interface ExecutorCommandRunner {
    suspend fun run(command: ExecutorCommand): ExecutorCommandResult
}
