package com.gaozay.smartflight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.settings.UserSettings
import kotlin.math.roundToInt

@Composable
fun SmartFlightTheme(
    settings: UserSettings = UserSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = smartFlightColorScheme(settings, darkTheme),
        typography = Typography,
        shapes = smartFlightShapes(settings.cornerStyle),
        content = content,
    )
}

private fun smartFlightColorScheme(settings: UserSettings, darkTheme: Boolean): ColorScheme {
    val seed = Color(if (settings.themePalette == ThemePalette.Custom) {
        settings.customSeedColorArgb
    } else {
        settings.themePalette.seedColorArgb
    })
    val contrastShift = when (settings.themeIntensity) {
        ThemeIntensity.Restrained -> 0.88f
        ThemeIntensity.Standard -> 1f
        ThemeIntensity.HighContrast -> 1.15f
    }
    return if (darkTheme) darkSeedScheme(seed, contrastShift) else lightSeedScheme(seed, contrastShift)
}

private fun lightSeedScheme(seed: Color, contrastShift: Float): ColorScheme {
    val primary = seed.tone(saturation = 0.78f * contrastShift, value = 0.42f / contrastShift.coerceAtLeast(1f))
    val secondary = seed.rotate(14f).tone(saturation = 0.34f, value = 0.54f)
    val tertiary = seed.rotate(-22f).tone(saturation = 0.28f, value = 0.64f)
    val background = seed.rotate(-10f).tone(saturation = 0.08f, value = 0.985f)
    val surface = seed.rotate(-8f).tone(saturation = 0.05f, value = 0.995f)
    val surfaceVariant = seed.rotate(4f).tone(saturation = 0.12f, value = 0.91f)
    val outline = seed.tone(saturation = 0.12f, value = 0.55f)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = seed.tone(saturation = 0.28f, value = 0.86f),
        onPrimaryContainer = seed.tone(saturation = 0.56f, value = 0.20f),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = seed.rotate(14f).tone(saturation = 0.18f, value = 0.90f),
        onSecondaryContainer = seed.rotate(14f).tone(saturation = 0.36f, value = 0.24f),
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = seed.rotate(-22f).tone(saturation = 0.16f, value = 0.92f),
        onTertiaryContainer = seed.rotate(-22f).tone(saturation = 0.34f, value = 0.24f),
        background = background,
        onBackground = Color(0xFF23232A),
        surface = surface,
        onSurface = Color(0xFF23232A),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = seed.tone(saturation = 0.16f, value = 0.34f),
        outline = outline,
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color.White,
        onErrorContainer = Color(0xFF410002),
    )
}

private fun darkSeedScheme(seed: Color, contrastShift: Float): ColorScheme {
    val primary = seed.tone(saturation = 0.36f * contrastShift, value = 0.82f)
    val secondary = seed.rotate(12f).tone(saturation = 0.24f, value = 0.76f)
    val tertiary = seed.rotate(-20f).tone(saturation = 0.22f, value = 0.78f)
    val background = seed.tone(saturation = 0.22f, value = 0.11f)
    val surface = seed.tone(saturation = 0.20f, value = 0.15f)
    val surfaceVariant = seed.tone(saturation = 0.18f, value = 0.24f)
    return darkColorScheme(
        primary = primary,
        onPrimary = seed.tone(saturation = 0.46f, value = 0.18f),
        primaryContainer = seed.tone(saturation = 0.38f, value = 0.30f),
        onPrimaryContainer = seed.tone(saturation = 0.20f, value = 0.94f),
        secondary = secondary,
        onSecondary = seed.rotate(12f).tone(saturation = 0.36f, value = 0.18f),
        secondaryContainer = seed.rotate(12f).tone(saturation = 0.28f, value = 0.30f),
        onSecondaryContainer = seed.rotate(12f).tone(saturation = 0.18f, value = 0.92f),
        tertiary = tertiary,
        onTertiary = seed.rotate(-20f).tone(saturation = 0.34f, value = 0.18f),
        tertiaryContainer = seed.rotate(-20f).tone(saturation = 0.24f, value = 0.30f),
        onTertiaryContainer = seed.rotate(-20f).tone(saturation = 0.16f, value = 0.94f),
        background = background,
        onBackground = Color(0xFFE7E2E6),
        surface = surface,
        onSurface = Color(0xFFE7E2E6),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = seed.tone(saturation = 0.12f, value = 0.78f),
        outline = seed.tone(saturation = 0.10f, value = 0.60f),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}

private fun smartFlightShapes(cornerStyle: CornerStyle): Shapes {
    val small = when (cornerStyle) {
        CornerStyle.Compact -> 8.dp
        CornerStyle.Standard -> 12.dp
        CornerStyle.Soft -> 18.dp
    }
    val medium = when (cornerStyle) {
        CornerStyle.Compact -> 12.dp
        CornerStyle.Standard -> 18.dp
        CornerStyle.Soft -> 26.dp
    }
    val large = when (cornerStyle) {
        CornerStyle.Compact -> 18.dp
        CornerStyle.Standard -> 28.dp
        CornerStyle.Soft -> 36.dp
    }
    return Shapes(
        extraSmall = RoundedCornerShape((small.value / 2f).dp),
        small = RoundedCornerShape(small),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        extraLarge = RoundedCornerShape(large + 8.dp),
    )
}

private fun Color.tone(saturation: Float, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = saturation.coerceIn(0f, 1f)
    hsv[2] = value.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(alphaInt(), hsv))
}

private fun Color.rotate(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(alphaInt(), hsv))
}

private fun Color.alphaInt(): Int = (alpha * 255).roundToInt().coerceIn(0, 255)
