package com.gaozay.smartflight.permission

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootAccessChecker @Inject constructor(
    private val rootAccessProbeRepository: RootAccessProbeRepository,
) {
    suspend fun check(): AccessCheckResult = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath == null) {
            return@withContext AccessCheckResult(
                title = "Root",
                status = AccessCheckStatus.Missing,
                summary = "未检测到 Root 二进制文件",
                recommendation = "如果设备没有 Root，请优先使用 Shizuku 或 ADB 初始化方案。",
                isBlocking = true,
                actionType = AccessActionType.Refresh,
            )
        }

        val snapshot = rootAccessProbeRepository.getSnapshot()
        val confirmed = snapshot.confirmedAvailable
        AccessCheckResult(
            title = "Root",
            status = if (confirmed) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = if (confirmed) "已确认 Root 授权可用" else "已检测到 Root 二进制文件",
            recommendation = if (confirmed) {
                "设备已完成一次 Root 授权验证，后续可继续接入 Root 执行器。"
            } else {
                "可主动测试一次 Root 授权；成功后会把 Root 标记为已确认可用。"
            },
            isBlocking = true,
            actionType = if (confirmed) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append("检测路径：")
                append(suPath)
                if (snapshot.lastProbeAtMillis > 0) {
                    append("。最近一次测试：")
                    append(snapshot.lastProbeSummary)
                } else if (!confirmed) {
                    append("。当前还没有执行过主动 Root 授权测试。")
                }
            },
            satisfiesRequirement = confirmed,
        )
    }

    suspend fun probeAuthorization(): AccessCheckResult = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath == null) {
            val result = AccessCheckResult(
                title = "Root",
                status = AccessCheckStatus.Missing,
                summary = "未检测到 Root 二进制文件",
                recommendation = "设备当前没有可用的 Root 入口，无法继续测试授权。",
                isBlocking = true,
                actionType = AccessActionType.Refresh,
            )
            rootAccessProbeRepository.updateSnapshot(
                RootProbeSnapshot(
                    confirmedAvailable = false,
                    lastProbeAtMillis = System.currentTimeMillis(),
                    lastProbeSummary = result.summary,
                ),
            )
            return@withContext result
        }

        val probe = runCatching {
            val process = ProcessBuilder(suPath, "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(1800, TimeUnit.MILLISECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!finished) {
                process.destroy()
            }
            RootCommandResult(
                finished = finished,
                exitCode = if (finished) process.exitValue() else null,
                output = output,
            )
        }.getOrNull()

        val confirmed = probe?.finished == true &&
            probe.exitCode == 0 &&
            probe.output.contains("uid=0")
        val summary = when {
            confirmed -> "Root 授权测试成功"
            probe == null -> "Root 授权测试失败：无法启动 su 进程"
            probe.finished != true -> "Root 授权测试超时，可能仍在等待授权"
            else -> "Root 授权测试失败，返回码 ${probe.exitCode ?: -1}"
        }

        rootAccessProbeRepository.updateSnapshot(
            RootProbeSnapshot(
                confirmedAvailable = confirmed,
                lastProbeAtMillis = System.currentTimeMillis(),
                lastProbeSummary = summary,
            ),
        )

        AccessCheckResult(
            title = "Root",
            status = if (confirmed) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = summary,
            recommendation = if (confirmed) {
                "SmartFlight 已确认可以拿到 root shell，后续可继续接入 Root 执行器。"
            } else {
                "请检查 Root 管理器是否弹出授权框，或确认 SmartFlight 是否被拒绝。"
            },
            isBlocking = true,
            actionType = if (confirmed) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append("执行命令：")
                append(suPath)
                append(" -c id")
                if (!probe?.output.isNullOrBlank()) {
                    append("。输出：")
                    append(probe?.output?.lineSequence()?.firstOrNull())
                }
            },
            satisfiesRequirement = confirmed,
        )
    }

    private fun findSuPath(): String? = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
    ).firstOrNull { File(it).exists() }
}

private data class RootCommandResult(
    val finished: Boolean,
    val exitCode: Int?,
    val output: String,
)
