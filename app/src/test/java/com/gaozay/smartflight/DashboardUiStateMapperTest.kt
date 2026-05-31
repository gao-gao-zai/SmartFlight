package com.gaozay.smartflight

import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUiStateMapperTest {
    private val mapper = DashboardUiStateMapper()

    @Test
    fun buildsDashboardStatusFromRuntimeSnapshot() {
        val state = mapper.buildSmartFlightUiState(
            settings = UserSettings(automationEnabled = true),
            runtimeSnapshot = RuntimeSnapshot(
                currentForegroundPackageName = "com.example.app",
                activeExecutorType = ExecutorType.Root,
                runtimeStatusSummary = "ready",
                runtimeStatusResult = ExecutionResult.Success,
                isWifiConnected = true,
                isBluetoothStateReadable = false,
                isMobileDataEnabled = null,
            ),
            appCount = 3,
            logCount = 2,
            accessGateState = com.gaozay.smartflight.permission.AccessGateState(),
            recentLogs = emptyList(),
        )

        assertEquals("com.example.app", state.foregroundApp)
        assertEquals("Root", state.runtimeExecutor)
        assertEquals("ready", state.runtimeLastCheck)
        assertEquals("已连接", state.wifiStatus)
        assertEquals("未授权，不可读", state.bluetoothStatus)
        assertEquals("未知", state.mobileDataStatus)
    }

    @Test
    fun executionLogMapperUsesProbeLabelAndFallbackDetail() {
        val item = ExecutionLogEntity(
            timestampMillis = 1L,
            triggerSource = "Manual",
            foregroundPackageName = null,
            foregroundAppLabel = null,
            screenState = ScreenState.Unknown.name,
            isWifiConnected = false,
            isWifiEnabled = false,
            isBluetoothEnabled = false,
            matchedRules = "",
            actionType = ExecutionAction.DoNothing.name,
            executorType = ExecutorType.Root.name,
            result = ExecutionResult.Failed.name,
            errorMessage = "飞行模式状态探测：失败",
        ).toUiItem()

        assertEquals("状态探测", item.action)
        assertEquals("Root", item.executor)
        assertEquals("失败", item.result)
        assertEquals("飞行模式状态探测：失败", item.detail)
    }
}
