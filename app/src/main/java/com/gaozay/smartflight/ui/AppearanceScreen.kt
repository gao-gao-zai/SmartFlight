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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.R
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
        item { SettingsSection(stringResource(R.string.display_mode)) { ChoiceRow(stringResource(R.string.mode), ThemeMode.entries, settings.themeMode, onSetThemeMode) } }
        item { SettingsSection(stringResource(R.string.color_palette)) {
            enumValues<ThemePalette>().forEach { palette: ThemePalette ->
                OptionRow(stringResource(palette.labelRes), settings.themePalette == palette, Color(palette.seedColorArgb)) { onSetThemePalette(palette) }
            }
        } }
        item { SettingsSection(stringResource(R.string.custom_seed_color)) {
            listOf<Int>(0xFF545D6D.toInt(), 0xFF657181.toInt(), 0xFF2F3948.toInt(), 0xFFA1859B.toInt(), 0xFF5E6D5A.toInt(), 0xFF73545D.toInt()).forEach { seed: Int ->
                OptionRow(
                    title = stringResource(R.string.theme_seed_color, Integer.toHexString(seed).takeLast(6).uppercase()),
                    selected = settings.themePalette == ThemePalette.Custom && settings.customSeedColorArgb == seed,
                    color = Color(seed),
                    onClick = { onSetCustomSeedColor(seed) },
                )
            }
        } }
        item { SettingsSection(stringResource(R.string.visual_intensity)) {
            ChoiceRow(stringResource(R.string.color_intensity), ThemeIntensity.entries, settings.themeIntensity, onSetThemeIntensity)
            ChoiceRow(stringResource(R.string.corner_style), CornerStyle.entries, settings.cornerStyle, onSetCornerStyle)
        } }
    }
}

@Composable
private fun ThemePreviewCard(settings: UserSettings) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.live_preview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${stringResource(settings.themePalette.labelRes)} · ${stringResource(settings.themeMode.labelRes)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(stringResource(R.string.success), StatusKind.Success)
                StatusBadge(stringResource(R.string.attention), StatusKind.Warning)
                StatusBadge(stringResource(R.string.failed), StatusKind.Error)
            }
            Button(onClick = {}) { Text(stringResource(R.string.primary_button)) }
        }
    }
}
