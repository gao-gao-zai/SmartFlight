package com.gaozay.smartflight.executor

import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkControlCommandMapperTest {
    private val mapper = NetworkControlCommandMapper()

    @Test
    fun readCommandForMapsNetworkControlModes() {
        assertEquals(
            ExecutorReadonlyCommands.ReadAirplaneModeState,
            mapper.readCommandFor(NetworkControlMode.AirplaneMode),
        )
        assertEquals(
            ExecutorReadonlyCommands.ReadMobileDataState,
            mapper.readCommandFor(NetworkControlMode.MobileData),
        )
    }

    @Test
    fun writeCommandForMapsNetworkControlModes() {
        assertTrue(
            mapper.writeCommandFor(NetworkControlMode.AirplaneMode, enabled = true)
                .rawCommand
                .contains("airplane-mode enable"),
        )
        assertEquals(
            "svc data disable",
            mapper.writeCommandFor(NetworkControlMode.MobileData, enabled = false).rawCommand,
        )
    }

    @Test
    fun summariesKeepExistingChineseCopy() {
        assertEquals("没有可用于读取飞行模式状态的执行器", mapper.noReadExecutorSummary(NetworkControlMode.AirplaneMode))
        assertEquals("没有可用于切换移动数据的执行器", mapper.noToggleExecutorSummary(NetworkControlMode.MobileData))
        assertEquals("无法解析当前飞行模式状态，已取消设置", mapper.unresolvedSetSummary(NetworkControlMode.AirplaneMode))
        assertEquals("无法解析当前移动数据状态，已取消切换", mapper.unresolvedToggleSummary(NetworkControlMode.MobileData))
        assertEquals("飞行模式写入失败", mapper.writeFailedSummary(NetworkControlMode.AirplaneMode))
        assertEquals("移动数据已处于开启状态", mapper.alreadyInStateSummary(NetworkControlMode.MobileData, enabled = true))
        assertEquals("飞行模式已关闭", mapper.enabledChangedSummary(NetworkControlMode.AirplaneMode, enabled = false))
    }

    @Test
    fun withModeAppliesModeAndParsesBinaryToggleState() {
        val mapped = mapper.withMode(
            mode = NetworkControlMode.MobileData,
            result = ExecutorCommandResult(
                executorType = ExecutorType.Root,
                executed = true,
                exitCode = 0,
                stdout = "1",
                summary = "raw",
            ),
            summary = "mapped",
        )

        assertEquals(NetworkControlMode.MobileData, mapped.controlMode)
        assertEquals(true, mapped.controlledEnabled)
        assertEquals("mapped", mapped.summary)
    }
}
