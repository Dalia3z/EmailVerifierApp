package com.example.emailverifier.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Green,
    onSecondary = Color.White,
    tertiary = Amber,
    background = Background,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF0F1B33),
    secondary = Green,
    tertiary = Amber,
    error = Red,
)

/**
 * Application theme: a standard Material 3 wrapper that supports light/dark mode.
 */
@Composable
fun EmailVerifierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
