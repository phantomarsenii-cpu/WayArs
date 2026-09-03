package com.wayars.app.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WayArsColorScheme = darkColorScheme(
    primary = WaNeonGreen,
    onPrimary = Color0,
    secondary = WaBlue,
    background = WaBackground,
    onBackground = WaTextPrimary,
    surface = WaSurface,
    onSurface = WaTextPrimary,
    surfaceVariant = WaSurfaceVariant,
    onSurfaceVariant = WaTextSecondary,
    error = WaRed
)

private val Color0 = androidx.compose.ui.graphics.Color(0xFF04140C)

@Composable
fun WayArsTheme(content: @Composable () -> Unit) {
    // Always dark: this app is explicitly designed for night driving.
    MaterialTheme(
        colorScheme = WayArsColorScheme,
        typography = WayArsTypography,
        content = content
    )
}
