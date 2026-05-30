package com.gaozay.smartflight.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.gaozay.smartflight.permission.AccessCheckResult

@Composable
internal fun AdbBootstrapActions(
    result: AccessCheckResult,
    onSetAdbBootstrapped: (Boolean) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
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
