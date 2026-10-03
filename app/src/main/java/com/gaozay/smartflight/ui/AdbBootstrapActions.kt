package com.gaozay.smartflight.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.gaozay.smartflight.R
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
            Text(result.copyLabel ?: stringResource(R.string.copy_commands))
        }
    }
    val markAsReady = !result.satisfiesRequirement
    OutlinedButton(onClick = { onSetAdbBootstrapped(markAsReady) }) {
        Text(if (markAsReady) stringResource(R.string.i_have_completed_adb_initialization) else stringResource(R.string.reset_adb_initialization_status))
    }
}
