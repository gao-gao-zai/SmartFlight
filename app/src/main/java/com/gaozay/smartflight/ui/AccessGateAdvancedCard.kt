package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
import com.gaozay.smartflight.permission.AccessActionType
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessKind

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
private fun AccessGateAdvancedAction(
    result: AccessCheckResult,
    onRequestShizukuPermission: () -> Unit,
    onProbeRootAccess: () -> Unit,
    onSetAdbBootstrapped: (Boolean) -> Unit,
) {
    if (result.kind == AccessKind.Shizuku && result.actionType == AccessActionType.RequestPermission) {
        OutlinedButton(onClick = onRequestShizukuPermission) {
            Text(stringResource(R.string.request_shizuku_permission))
        }
    }
    if (result.kind == AccessKind.Root && result.actionType == AccessActionType.RequestPermission) {
        OutlinedButton(onClick = onProbeRootAccess) {
            Text(stringResource(R.string.test_root_authorization))
        }
    }
    if (result.kind == AccessKind.Adb) {
        AdbBootstrapActions(
            result = result,
            onSetAdbBootstrapped = onSetAdbBootstrapped,
        )
    }
}
