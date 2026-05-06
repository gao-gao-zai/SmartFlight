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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessGateState

@Composable
fun AccessGateScreen(
    state: AccessGateState,
    onRefresh: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onEnterApp: () -> Unit,
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
                    text = "自动化功能需要至少一种高级执行能力，并授予使用情况访问权限。",
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
            )
        }
        item {
            AccessCheckCard(
                icon = Icons.Rounded.VerifiedUser,
                result = state.usageStatsAccess,
                actionText = "打开使用情况访问设置",
                onAction = onOpenUsageAccessSettings,
            )
        }
        item {
            AccessCheckCard(
                icon = Icons.Rounded.Notifications,
                result = state.notificationAccess,
                actionText = "打开通知设置",
                onAction = onOpenNotificationSettings,
            )
        }
        item {
            AccessCheckCard(
                icon = Icons.Rounded.BatterySaver,
                result = state.batteryOptimization,
                actionText = "打开电池优化设置",
                onAction = onOpenBatteryOptimizationSettings,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新检测")
                }
                OutlinedButton(
                    onClick = onEnterApp,
                    enabled = state.canEnterApp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.canEnterApp) "进入应用" else "等待满足接入条件")
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
                    text = "当前执行器：${state.advancedAccess.selectedExecutorType.label}",
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
            }
        }
    }
}

@Composable
private fun AccessCheckCard(
    icon: ImageVector,
    result: AccessCheckResult,
    actionText: String,
    onAction: () -> Unit,
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
                Icon(icon, contentDescription = result.title)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AccessResultRow(result = result)
            OutlinedButton(onClick = onAction) {
                Icon(Icons.Rounded.Settings, contentDescription = actionText)
                Spacer(modifier = Modifier.size(8.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun AccessResultRow(result: AccessCheckResult) {
    val color = if (result.available) Color(0xFF1FA971) else Color(0xFFE55D87)
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
                text = result.summary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.recommendation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
