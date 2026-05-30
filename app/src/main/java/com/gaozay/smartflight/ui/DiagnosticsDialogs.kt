package com.gaozay.smartflight.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun ConfirmActionDialog(action: String, modeLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title = when (action) {
        "probe" -> "探测当前控制状态"
        "toggle" -> "手动切换当前模式"
        "clear" -> "清空全部日志"
        else -> "确认操作"
    }
    val description = when (action) {
        "probe" -> "即将读取设备当前的 $modeLabel 状态，并写入一条诊断日志。"
        "toggle" -> "即将直接切换当前联网控制方式（$modeLabel）。该操作仅用于排障，并会写入日志。"
        "clear" -> "即将清空当前保存的执行日志，此操作不可从应用内恢复。"
        else -> "请确认是否继续。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (action == "clear") Icons.Rounded.Error else Icons.Rounded.PowerSettingsNew, contentDescription = null) },
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
