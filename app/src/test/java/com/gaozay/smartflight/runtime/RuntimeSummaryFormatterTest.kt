package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSummaryFormatterTest {
    @Test
    fun permanentDisabledSummaryUsesDisabledCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(automationEnabled = false),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = ExecutionResult.Pending,
            ),
        )

        assertEquals("自动化已永久禁用", summary)
    }

    @Test
    fun temporaryDisableSummaryTakesPriority() {
        val nowMillis = 10_000L

        val summary = buildRuntimeSummary(
            settings = UserSettings(
                automationEnabled = true,
                temporaryDisableMode = AutomationDisableMode.For1Minute,
                temporaryDisableUntilMillis = nowMillis + 21_500L,
            ),
            snapshot = RuntimeSnapshot(
                isScreenOffDisconnectScheduled = true,
                pendingScreenOffDisconnectAtMillis = nowMillis + 5_000L,
            ),
            nowMillis = nowMillis,
        )

        assertEquals("已临时禁用，剩余 22 秒", summary)
    }

    @Test
    fun appExitScheduledSummaryUsesPendingRemainingSeconds() {
        val nowMillis = 10_000L

        val summary = buildRuntimeSummary(
            settings = UserSettings(automationEnabled = true, appExitDelaySeconds = 30),
            snapshot = RuntimeSnapshot(
                isAppExitDisconnectScheduled = true,
                pendingAppExitDisconnectAtMillis = nowMillis + 12_001L,
            ),
            nowMillis = nowMillis,
        )

        assertEquals("联网应用已离开前台，将在 13 秒后断网", summary)
    }

    @Test
    fun screenOffScheduledSummaryUsesFallbackDelayWhenPendingTimeMissing() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(automationEnabled = true, screenOffDelaySeconds = 45),
            snapshot = RuntimeSnapshot(isScreenOffDisconnectScheduled = true),
            nowMillis = 10_000L,
        )

        assertEquals("屏幕已熄灭，将在 45 秒后断网", summary)
    }

    @Test
    fun screenOffWithoutForegroundMonitoringUsesPauseCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(
                automationEnabled = true,
                monitorForegroundWhenScreenOff = false,
            ),
            snapshot = RuntimeSnapshot(screenState = ScreenState.ScreenOff),
        )

        assertEquals("屏幕已熄灭，已按设置暂停前台应用监听", summary)
    }

    @Test
    fun appForegroundCancelSuccessUsesAppExitCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(automationEnabled = true),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.CancelScheduledDisconnect,
                lastActionResult = ExecutionResult.Success,
                lastTriggerSource = TriggerSource.AppForegroundChanged,
            ),
        )

        assertEquals("联网应用已重新进入前台，已取消待执行的离开应用延迟断网", summary)
    }

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
    fun mobileDataReconnectSkippedUsesModeAwareCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(
                networkControlMode = NetworkControlMode.MobileData,
                preserveWifiState = true,
                preserveBluetoothState = false,
            ),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.ReconnectNow,
                lastActionResult = ExecutionResult.Skipped,
            ),
        )

        assertEquals("移动数据原本已开启，无需重复恢复联网；保留 Wi‑Fi 状态：no-op", summary)
    }

    @Test
    fun mobileDataProbeSummaryUsesMobileDataState() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(networkControlMode = NetworkControlMode.MobileData),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = ExecutionResult.Success,
                lastTriggerSource = TriggerSource.Manual,
                isMobileDataEnabled = true,
            ),
        )

        assertEquals("移动数据当前已开启", summary)
    }

    @Test
    fun failedManualProbeFallsBackToModeAwareCopy() {
        val summary = buildRuntimeSummary(
            settings = UserSettings(networkControlMode = NetworkControlMode.AirplaneMode),
            snapshot = RuntimeSnapshot(
                lastAction = ExecutionAction.DoNothing,
                lastActionResult = ExecutionResult.Failed,
                lastTriggerSource = TriggerSource.Manual,
                lastActionReason = "",
            ),
        )

        assertEquals("飞行模式状态探测失败", summary)
    }
}
