package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessGateState

@Composable
internal fun AccessSummaryCard(state: AccessGateState) {
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
internal fun AccessSectionCard(
    title: String,
    icon: ImageVector,
    checks: List<AccessCheckResult>,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
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
                AccessGateAdvancedAction(
                    result = result,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onProbeRootAccess = onProbeRootAccess,
                    onSetAdbBootstrapped = onSetAdbBootstrapped,
                )
            }
        }
    }
}

@Composable
internal fun SystemAccessCard(
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
internal fun AccessResultRow(result: AccessCheckResult) {
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

@Composable
private fun AccessGateAdvancedAction(
    result: AccessCheckResult,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
) {
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
        AdbBootstrapActions(
            result = result,
            onSetAdbBootstrapped = onSetAdbBootstrapped,
        )
    }
}
