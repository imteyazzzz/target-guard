package com.remoteappblocker.target.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = AccentCyan,
    onSecondary = Color.Black,
    tertiary = WarningAmber,
    background = DarkSurface,
    onBackground = Slate100,
    surface = DarkCard,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun RemoteAppBlockerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
