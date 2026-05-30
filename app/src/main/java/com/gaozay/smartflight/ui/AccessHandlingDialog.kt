package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult

@Composable
internal fun AccessHandlingDialog(
    result: AccessCheckResult,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onCopyAdbCommands: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (result.satisfiesRequirement) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                contentDescription = null,
            )
        },
        title = { Text(result.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(result.summary)
                Text(result.recommendation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (result.title) {
                    "使用情况访问权限" -> TextButton(onClick = onOpenUsageAccessSettings) { Text("去设置") }
                    "通知权限" -> TextButton(onClick = onOpenNotificationSettings) { Text("去设置") }
                    "电池优化" -> TextButton(onClick = onOpenBatteryOptimizationSettings) { Text("去设置") }
                    "Shizuku" -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onRequestShizukuPermission) { Text("请求授权") }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text("重新检测") }
                        else -> Unit
                    }
                    "Root" -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onProbeRootAccess) { Text("测试授权") }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text("重新检测") }
                        else -> Unit
                    }
                    "ADB 初始化" -> {
                        result.copyText?.let {
                            TextButton(onClick = onCopyAdbCommands) {
                                Text(result.copyLabel ?: "复制命令")
                            }
                        }
                        TextButton(onClick = { onSetAdbBootstrapped(!result.satisfiesRequirement) }) {
                            Text(if (result.satisfiesRequirement) "重置状态" else "标记完成")
                        }
                    }
                    else -> if (result.actionType == AccessActionType.Refresh) {
                        TextButton(onClick = onRefresh) { Text("重新检测") }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
