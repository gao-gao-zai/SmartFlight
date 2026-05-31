package com.gaozay.smartflight.permission

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessResultFormatterTest {
    private val formatter = AccessResultFormatter()

    @Test
    fun probeReasonIncludesModeExecutorOutputAndError() {
        val reason = formatter.buildProbeDetailReason(
            executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = null,
                stdout = "unexpected",
                stderr = "denied",
                summary = "无法解析",
            ),
        )

        assertEquals("移动数据状态探测：无法解析 · 执行器：Root · 输出：unexpected · 错误：denied", reason)
    }

    @Test
    fun actionReasonKeepsExistingCopy() {
        val reason = formatter.buildActionDetailReason(
            reasonPrefix = "手动切换飞行模式",
            result = executorResult(summary = "飞行模式已开启"),
        )

        assertEquals("手动切换飞行模式：飞行模式已开启 · 执行器：Root", reason)
    }

    @Test
    fun airplaneReasonAppendsMobileDataRestoreSummary() {
        val reason = formatter.buildAirplaneModeDetailReason(
            reasonPrefix = "自动关闭飞行模式",
            airplaneResult = executorResult(summary = "飞行模式已关闭"),
            restoreMobileDataResult = executorResult(
                controlMode = NetworkControlMode.MobileData,
                controlledEnabled = true,
                summary = "移动数据已开启",
            ),
        )

        assertEquals("自动关闭飞行模式：飞行模式已关闭 · 执行器：Root · 移动数据恢复：移动数据已开启", reason)
    }

    @Test
    fun passiveAccessSummaryUsesExecutorOrFirstGatingIssue() {
        assertEquals(
            "当前可用通道：Root",
            formatter.buildPassiveAccessSummary(
                AdvancedAccessState(selectedExecutorType = ExecutorType.Root),
            ),
        )
        assertEquals(
            "尚无可用执行器",
            formatter.buildPassiveAccessSummary(AdvancedAccessState()),
        )
    }
}
