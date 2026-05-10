package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessGateState

@Composable
fun AccessGateScreen(
    state: AccessGateState,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
    onAutoGrantCompanionPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SmartFlight 接入检查",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "自动化功能至少需要一种高级执行能力，并授予使用情况访问权限。自动授权只会在你点击按钮后尝试执行。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AccessSummaryCard(state = state)
        }
        item {
            AccessSectionCard(
                title = "高级执行能力",
                icon = Icons.Rounded.Security,
                checks = state.advancedAccess.checks,
                onRequestShizukuPermission = onRequestShizukuPermission,
                onProbeRootAccess = onProbeRootAccess,
                onSetAdbBootstrapped = onSetAdbBootstrapped,
            )
        }
        item {
            SystemAccessCard(
                usageStatsAccess = state.usageStatsAccess,
                notificationAccess = state.notificationAccess,
                batteryOptimization = state.batteryOptimization,
                canAutoGrant = state.advancedAccess.isAvailable,
                onAutoGrantCompanionPermissions = onAutoGrantCompanionPermissions,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.canEnterApp) "刷新状态" else "继续检查")
                }
                if (state.advisoryChecks.isNotEmpty()) {
                    Text(
                        text = "建议项未完成：${state.advisoryChecks.joinToString { it.title }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessSummaryCard(state: AccessGateState) {
    val color = if (state.canEnterApp) Color(0xFF1FA971) else Color(0xFFF5A623)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.canEnterApp) "已满足核心运行门槛" else "仍需完成接入条件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.canEnterApp) {
                        "当前执行器：${state.advancedAccess.selectedExecutorType.label}"
                    } else {
                        "阻塞项剩余 ${state.blockingChecks.size} 个，建议项 ${state.advisoryChecks.size} 个"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccessSectionCard(
    title: String,
    icon: ImageVector,
    checks: List<AccessCheckResult>,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            checks.forEach { result ->
                AccessResultRow(result = result)
                if (result.title == "Shizuku" && result.actionType == AccessActionType.RequestPermission) {
                    OutlinedButton(onClick = onRequestShizukuPermission) {
                        Text("请求 Shizuku 授权")
                    }
                }
                if (result.title == "Root" && result.actionType == AccessActionType.RequestPermission) {
                    OutlinedButton(onClick = onProbeRootAccess) {
                        Text("测试 Root 授权")
                    }
                }
                if (result.title == "ADB 初始化") {
                    result.copyText?.let { copyText ->
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(copyText))
                            },
                        ) {
                            Text(result.copyLabel ?: "复制命令")
                        }
                    }
                    val markAsReady = !result.satisfiesRequirement
                    OutlinedButton(onClick = { onSetAdbBootstrapped(markAsReady) }) {
                        Text(if (markAsReady) "我已完成 ADB 初始化" else "重置 ADB 初始化状态")
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemAccessCard(
    usageStatsAccess: AccessCheckResult,
    notificationAccess: AccessCheckResult,
    batteryOptimization: AccessCheckResult,
    canAutoGrant: Boolean,
    onAutoGrantCompanionPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = "一般权限")
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "一般权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = onAutoGrantCompanionPermissions,
                modifier = Modifier.fillMaxWidth(),
                enabled = canAutoGrant,
            ) {
                Text("尝试自动授权")
            }
            AccessResultRow(result = usageStatsAccess)
            OutlinedButton(onClick = onOpenUsageAccessSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "打开使用情况访问设置")
                Spacer(modifier = Modifier.size(8.dp))
                Text("打开使用情况访问设置")
            }
            AccessResultRow(result = notificationAccess)
            OutlinedButton(onClick = onOpenNotificationSettings) {
                Icon(Icons.Rounded.Notifications, contentDescription = "打开通知设置")
                Spacer(modifier = Modifier.size(8.dp))
                Text("打开通知设置")
            }
            AccessResultRow(result = batteryOptimization)
            OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                Icon(Icons.Rounded.BatterySaver, contentDescription = "打开电池优化设置")
                Spacer(modifier = Modifier.size(8.dp))
                Text("打开电池优化设置")
            }
        }
    }
}

@Composable
private fun AccessResultRow(result: AccessCheckResult) {
    val color = when {
        result.satisfiesRequirement -> Color(0xFF1FA971)
        result.isBlocking -> Color(0xFFE55D87)
        else -> Color(0xFFF5A623)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${result.summary} · ${result.statusLabel}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.recommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.detail?.let { detail ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
