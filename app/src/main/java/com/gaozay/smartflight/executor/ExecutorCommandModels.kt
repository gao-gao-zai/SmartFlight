package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType

data class ExecutorCommand(
    val rawCommand: String,
    val purpose: String,
)

object ExecutorReadonlyCommands {
    val ReadAirplaneModeState = ExecutorCommand(
        rawCommand = "settings get global airplane_mode_on",
        purpose = "读取飞行模式状态",
    )
}

data class ExecutorCommandResult(
    val executorType: ExecutorType,
    val executed: Boolean,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val summary: String,
)
