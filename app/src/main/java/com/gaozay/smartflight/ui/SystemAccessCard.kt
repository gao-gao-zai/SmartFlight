package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.AccessibilityNew
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.permission.AccessCheckResult

@Composable
internal fun SystemAccessCard(
    usageStatsAccess: AccessCheckResult,
    accessibilityAccess: AccessCheckResult,
    notificationAccess: AccessCheckResult,
    batteryOptimization: AccessCheckResult,
    canAutoGrant: Boolean,
    onAutoGrantCompanionPermissions: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
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
            AccessResultRow(result = accessibilityAccess)
            OutlinedButton(onClick = onOpenAccessibilitySettings) {
                Icon(Icons.Rounded.AccessibilityNew, contentDescription = "打开无障碍设置")
                Spacer(modifier = Modifier.size(8.dp))
                Text("打开无障碍设置")
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
