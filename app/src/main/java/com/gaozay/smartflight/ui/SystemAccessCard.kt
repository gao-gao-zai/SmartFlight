package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
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
                Icon(Icons.Rounded.VerifiedUser, contentDescription = stringResource(R.string.general_permissions))
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.general_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = onAutoGrantCompanionPermissions,
                modifier = Modifier.fillMaxWidth(),
                enabled = canAutoGrant,
            ) {
                Text(stringResource(R.string.try_automatic_permission_grants))
            }
            AccessResultRow(result = usageStatsAccess)
            OutlinedButton(onClick = onOpenUsageAccessSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.open_usage_access_settings))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.open_usage_access_settings))
            }
            AccessResultRow(result = accessibilityAccess)
            OutlinedButton(onClick = onOpenAccessibilitySettings) {
                Icon(Icons.Rounded.AccessibilityNew, contentDescription = stringResource(R.string.open_accessibility_settings))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.open_accessibility_settings))
            }
            AccessResultRow(result = notificationAccess)
            OutlinedButton(onClick = onOpenNotificationSettings) {
                Icon(Icons.Rounded.Notifications, contentDescription = stringResource(R.string.open_notification_settings))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.open_notification_settings))
            }
            AccessResultRow(result = batteryOptimization)
            OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                Icon(Icons.Rounded.BatterySaver, contentDescription = stringResource(R.string.open_battery_optimization_settings))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.open_battery_optimization_settings))
            }
        }
    }
}
