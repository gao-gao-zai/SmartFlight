package com.gaozay.smartflight.executor

import android.content.pm.PackageManager
import com.gaozay.smartflight.domain.model.ExecutorType
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuExecutorValidator @Inject constructor() : ExecutorValidator {
    override suspend fun validate(): ExecutorValidationResult {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ExecutorValidationResult(
                executorType = ExecutorType.Shizuku,
                isReady = false,
                summary = "Shizuku 执行器未就绪",
                detail = "Binder 尚未连接，无法调用 Shizuku 服务。",
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val remoteUid = runCatching { Shizuku.getUid() }.getOrNull()

        return ExecutorValidationResult(
            executorType = ExecutorType.Shizuku,
            isReady = granted,
            summary = if (granted) "Shizuku 执行器已就绪" else "Shizuku 执行器缺少授权",
            detail = buildString {
                append("Binder 已连接")
                if (remoteUid != null) {
                    append("，后端 UID=")
                    append(remoteUid)
                    append(if (remoteUid == 0) "（ROOT）" else if (remoteUid == 2000) "（ADB）" else "")
                }
            },
        )
    }
}
