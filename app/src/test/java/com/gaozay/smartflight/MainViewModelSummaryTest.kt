package com.gaozay.smartflight

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelSummaryTest {
    @Test
    fun airplaneModeDisconnectSummaryUsesAirplaneCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(networkControlMode = NetworkControlMode.AirplaneMode),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DisconnectNow,
                lastActionResult = ExecutionResult.Success,
                isAirplaneModeEnabled = true,
            ),
        )

        assertEquals("已开启飞行模式，当前处于断网状态", summary)
    }

    @Test
    fun mobileDataDisconnectSummaryUsesModeAwareCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(
                networkControlMode = NetworkControlMode.MobileData,
                preserveWifiState = true,
                preserveBluetoothState = true,
            ),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DisconnectNow,
                lastActionResult = ExecutionResult.Success,
                isMobileDataEnabled = false,
            ),
        )

        assertEquals("已关闭移动数据，当前处于断网状态；保留 Wi‑Fi 状态：no-op；保留蓝牙状态：no-op", summary)
    }

    @Test
    fun mobileDataProbeSummaryUsesMobileDataState() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(networkControlMode = NetworkControlMode.MobileData),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = ExecutionResult.Success,
                lastTriggerSource = com.gaozay.smartflight.domain.model.TriggerSource.Manual,
                isMobileDataEnabled = true,
            ),
        )

        assertEquals("移动数据当前已开启", summary)
    }
}
