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

object ExecutorWriteCommands {
    fun setAirplaneModeState(enabled: Boolean): ExecutorCommand = ExecutorCommand(
        rawCommand = "settings put global airplane_mode_on ${if (enabled) "1" else "0"}",
        purpose = if (enabled) "开启飞行模式" else "关闭飞行模式",
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
