package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
