package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.update.UpdateUiState

@Composable
internal fun UpdatePromptDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onSkipVersion: (String) -> Unit,
) {
    when (state) {
        UpdateUiState.Idle,
        is UpdateUiState.Checking -> Unit
        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("当前已是最新版本") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
            )
        }
        is UpdateUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("检查更新失败") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
            )
        }
        is UpdateUiState.UpdateAvailable -> {
            val release = state.release
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
                title = { Text("发现新版本 ${release.tagName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            release.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            release.body.ifBlank { "发布页包含本次更新的完整说明。" },
                            modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${release.source.label} · ${release.pageUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCopyLink(release.pageUrl) },
                                modifier = Modifier.weight(1f),
                            ) { Text("复制链接") }
                            OutlinedButton(
                                onClick = { onOpenLink(release.pageUrl) },
                                modifier = Modifier.weight(1f),
                            ) { Text("浏览器打开") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) { Text("本次忽略") }
                            TextButton(
                                onClick = { onSkipVersion(release.tagName) },
                                modifier = Modifier.weight(1f),
                            ) { Text("跳过此版本") }
                        }
                        Spacer(Modifier.size(0.dp))
                    }
                },
            )
        }
    }
}
