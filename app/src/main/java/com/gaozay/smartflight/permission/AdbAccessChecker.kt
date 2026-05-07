package com.gaozay.smartflight.permission

import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbAccessChecker @Inject constructor(
    private val adbBootstrapRepository: AdbBootstrapRepository,
) {
    suspend fun check(): AccessCheckResult {
        val snapshot = adbBootstrapRepository.getSnapshot()
        val bootstrapped = snapshot.bootstrapped
        val commandText = buildString {
            append("adb devices\n")
            append("adb shell settings get global airplane_mode_on\n")
            append("adb shell cmd appops get com.gaozay.smartflight\n")
        }
        return AccessCheckResult(
            title = "ADB 初始化",
            status = if (bootstrapped) AccessCheckStatus.Ready else AccessCheckStatus.Missing,
            summary = if (bootstrapped) "已记录 ADB 初始化完成" else "尚未完成 ADB 初始化",
            recommendation = if (bootstrapped) {
                "已记录一份 ADB 初始化完成状态，后续可继续补真实命令链路。"
            } else {
                "先确认设备可被 adb 识别，再执行检查命令，确认 SmartFlight 已具备继续接入 ADB 执行器的前置条件。"
            },
            isBlocking = true,
            actionType = AccessActionType.Refresh,
            detail = if (bootstrapped) {
                buildString {
                    append("命令版本 v")
                    append(snapshot.commandVersion)
                    if (snapshot.completedAtMillis > 0) {
                        append("，记录时间：")
                        append(DateFormat.getDateTimeInstance().format(Date(snapshot.completedAtMillis)))
                    }
                    append("。当前仍缺少真实执行命令的联调。")
                }
            } else {
                "检查顺序：1. 打开开发者选项；2. 打开 USB 调试；3. 连接电脑后执行下方命令；4. 确认无误后再标记初始化完成。"
            },
            copyText = commandText,
            copyLabel = "复制 ADB 检查命令",
        )
    }
}
