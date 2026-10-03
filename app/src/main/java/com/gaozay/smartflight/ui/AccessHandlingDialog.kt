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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessKind

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
    onOpenAccessibilitySettings: () -> Unit,
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
                when (result.kind) {
                    AccessKind.UsageStats -> TextButton(onClick = onOpenUsageAccessSettings) { Text(stringResource(R.string.open_settings)) }
                    AccessKind.Accessibility -> TextButton(onClick = onOpenAccessibilitySettings) { Text(stringResource(R.string.open_settings)) }
                    AccessKind.Notifications -> TextButton(onClick = onOpenNotificationSettings) { Text(stringResource(R.string.open_settings)) }
                    AccessKind.BatteryOptimization -> TextButton(onClick = onOpenBatteryOptimizationSettings) { Text(stringResource(R.string.open_settings)) }
                    AccessKind.Shizuku -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onRequestShizukuPermission) { Text(stringResource(R.string.request_permission)) }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text(stringResource(R.string.check_again)) }
                        else -> Unit
                    }
                    AccessKind.Root -> when (result.actionType) {
                        AccessActionType.RequestPermission -> TextButton(onClick = onProbeRootAccess) { Text(stringResource(R.string.test_authorization)) }
                        AccessActionType.Refresh -> TextButton(onClick = onRefresh) { Text(stringResource(R.string.check_again)) }
                        else -> Unit
                    }
                    AccessKind.Adb -> {
                        result.copyText?.let {
                            TextButton(onClick = onCopyAdbCommands) {
                                Text(result.copyLabel ?: stringResource(R.string.copy_commands))
                            }
                        }
                        TextButton(onClick = { onSetAdbBootstrapped(!result.satisfiesRequirement) }) {
                            Text(if (result.satisfiesRequirement) stringResource(R.string.reset_status) else stringResource(R.string.mark_complete))
                        }
                    }
                    else -> if (result.actionType == AccessActionType.Refresh) {
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.check_again)) }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
