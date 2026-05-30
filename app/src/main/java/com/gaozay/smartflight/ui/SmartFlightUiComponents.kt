package com.gaozay.smartflight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.settings.AutomationDisableMode

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun NumberRow(title: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { onValueChange(value - 5) }) { Text("-") }
        Text("$value 秒", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onValueChange(value + 5) }) { Text("+") }
    }
}

@Composable
internal fun TextInputRow(title: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        singleLine = true,
    )
}

@Composable
internal fun <T> ChoiceRow(title: String, options: List<T>, selected: T, onSelect: (T) -> Unit) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        options.forEach { option ->
            val label = when (option) {
                is NetworkControlMode -> option.label
                is ExecutorType -> option.label
                is ThemeMode -> option.label
                is ThemeIntensity -> option.label
                is CornerStyle -> option.label
                is AutomationDisableMode -> option.label
                else -> option.name
            }
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun OptionRow(title: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(12.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun InfoRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
internal fun AccessSummaryRow(
    title: String,
    summary: String,
    ready: Boolean,
    onBadgeClick: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(
            text = if (ready) "正常" else "处理",
            kind = if (ready) StatusKind.Success else StatusKind.Warning,
            onClick = onBadgeClick,
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal enum class StatusKind { Success, Warning, Error }

@Composable
internal fun StatusBadge(text: String, kind: StatusKind, onClick: (() -> Unit)? = null) {
    val color = when (kind) {
        StatusKind.Success -> Color(0xFF2E7D5B)
        StatusKind.Warning -> Color(0xFF9A6A00)
        StatusKind.Error -> MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = { onClick?.invoke() },
        label = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = when (kind) {
                    StatusKind.Success -> Icons.Rounded.CheckCircle
                    StatusKind.Warning -> Icons.Rounded.Schedule
                    StatusKind.Error -> Icons.Rounded.Error
                },
                contentDescription = null,
                tint = color,
            )
        },
        enabled = onClick != null,
    )
}
