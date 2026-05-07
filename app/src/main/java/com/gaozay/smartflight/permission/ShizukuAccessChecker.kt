package com.gaozay.smartflight.permission

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun check(): AccessCheckResult {
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        if (!installed) {
            return AccessCheckResult(
                title = "Shizuku",
                status = AccessCheckStatus.Missing,
                summary = "未检测到 Shizuku 应用",
                recommendation = "推荐安装并启动 Shizuku，这是非 Root 设备上更稳定的高级权限方案。",
                isBlocking = true,
                actionType = AccessActionType.Refresh,
                detail = "参考 Shizuku 官方文档，应用需要先安装并启动 Shizuku，再通过其 API 请求授权。",
            )
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return AccessCheckResult(
                title = "Shizuku",
                status = AccessCheckStatus.Detected,
                summary = "已安装 Shizuku，但服务当前未运行",
                recommendation = "请先打开 Shizuku 并完成启动，再返回 SmartFlight 重新检测。",
                isBlocking = true,
                actionType = AccessActionType.Refresh,
                detail = "官方 API 需要先获得 Binder，Binder 未就绪时无法继续检查授权状态。",
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val shouldShowRationale = runCatching {
            Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)
        val remoteUid = runCatching { Shizuku.getUid() }.getOrNull()

        return AccessCheckResult(
            title = "Shizuku",
            status = if (granted) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = if (granted) {
                "Shizuku 服务已运行，且已授权给 SmartFlight"
            } else {
                "Shizuku 服务已运行，但 SmartFlight 还未获得授权"
            },
            recommendation = if (granted) {
                "可继续使用 Shizuku 作为高级执行通道。"
            } else if (shouldShowRationale) {
                "Shizuku 权限曾被拒绝，请在 Shizuku 管理界面重新允许 SmartFlight。"
            } else {
                "下一步可直接发起 Shizuku 授权请求。"
            },
            isBlocking = true,
            actionType = if (granted) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append("Binder 已连接")
                if (remoteUid != null) {
                    append("，远端 UID：")
                    append(remoteUid)
                    append(if (remoteUid == 0) "（ROOT）" else if (remoteUid == 2000) "（ADB）" else "")
                }
                if (!granted) {
                    append("。")
                    append(
                        if (shouldShowRationale) {
                            "当前更像是被用户拒绝过。"
                        } else {
                            "当前尚未授权，可继续请求权限。"
                        },
                    )
                }
            },
            satisfiesRequirement = granted,
        )
    }
}
