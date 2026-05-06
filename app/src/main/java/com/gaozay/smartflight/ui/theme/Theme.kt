package com.gaozay.smartflight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = FlightBlue,
    secondary = FlightSky,
    tertiary = FlightMint,
)

private val DarkColors = darkColorScheme(
    primary = FlightSky,
    secondary = FlightMint,
    tertiary = FlightRose,
    background = NightBase,
    surface = NightSurface,
)

@Composable
fun SmartFlightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

