package com.example.omni_guard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberTextDim,
    tertiary = CyberYellow,
    background = CyberDarkBG,
    surface = CyberPanelBG,
    onPrimary = CyberDarkBG,
    onSecondary = CyberCyan,
    onTertiary = CyberDarkBG,
    onBackground = CyberCyan,
    onSurface = CyberCyan,
    error = CyberRed
)

@Composable
fun OMNI_GUARDTheme(
    darkTheme: Boolean = true, // Force dark theme for the cyber look
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
