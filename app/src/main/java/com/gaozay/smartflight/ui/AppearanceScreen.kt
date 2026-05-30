package com.gaozay.smartflight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.settings.UserSettings

@Composable
internal fun AppearanceScreen(
    settings: UserSettings,
    innerPadding: PaddingValues,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetThemePalette: (ThemePalette) -> Unit,
    onSetCustomSeedColor: (Int) -> Unit,
    onSetThemeIntensity: (ThemeIntensity) -> Unit,
    onSetCornerStyle: (CornerStyle) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ThemePreviewCard(settings) }
        item { SettingsSection("显示模式") { ChoiceRow("模式", ThemeMode.entries, settings.themeMode, onSetThemeMode) } }
        item { SettingsSection("配色风格") {
            enumValues<ThemePalette>().forEach { palette: ThemePalette ->
                OptionRow(palette.label, settings.themePalette == palette, Color(palette.seedColorArgb)) { onSetThemePalette(palette) }
            }
        } }
        item { SettingsSection("自定义 seed color") {
            listOf<Int>(0xFF545D6D.toInt(), 0xFF657181.toInt(), 0xFF2F3948.toInt(), 0xFFA1859B.toInt(), 0xFF5E6D5A.toInt(), 0xFF73545D.toInt()).forEach { seed: Int ->
                OptionRow(
                    title = "Seed #${Integer.toHexString(seed).takeLast(6).uppercase()}",
                    selected = settings.themePalette == ThemePalette.Custom && settings.customSeedColorArgb == seed,
                    color = Color(seed),
                    onClick = { onSetCustomSeedColor(seed) },
                )
            }
        } }
        item { SettingsSection("显示强度") {
            ChoiceRow("颜色强度", ThemeIntensity.entries, settings.themeIntensity, onSetThemeIntensity)
            ChoiceRow("圆角风格", CornerStyle.entries, settings.cornerStyle, onSetCornerStyle)
        } }
    }
}

@Composable
private fun ThemePreviewCard(settings: UserSettings) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${settings.themePalette.label} · ${settings.themeMode.label}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge("成功", StatusKind.Success)
                StatusBadge("注意", StatusKind.Warning)
                StatusBadge("失败", StatusKind.Error)
            }
            Button(onClick = {}) { Text("主按钮") }
        }
    }
}
