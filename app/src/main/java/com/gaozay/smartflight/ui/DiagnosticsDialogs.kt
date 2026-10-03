package com.gaozay.smartflight.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gaozay.smartflight.R

@Composable
internal fun ConfirmActionDialog(action: String, modeLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val title = when (action) {
        "probe" -> stringResource(R.string.probe_current_control_state)
        "toggle" -> stringResource(R.string.manually_toggle_current_mode)
        "clear" -> stringResource(R.string.clear_all_logs)
        else -> stringResource(R.string.confirm_action)
    }
    val description = when (action) {
        "probe" -> stringResource(R.string.diagnostics_probe_description, modeLabel)
        "toggle" -> stringResource(R.string.diagnostics_toggle_description, modeLabel)
        "clear" -> stringResource(R.string.clear_all_saved_execution_logs_this_cannot_be_undone_from_the_app)
        else -> stringResource(R.string.confirm_whether_to_continue)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (action == "clear") Icons.Rounded.Error else Icons.Rounded.PowerSettingsNew, contentDescription = null) },
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(title) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
