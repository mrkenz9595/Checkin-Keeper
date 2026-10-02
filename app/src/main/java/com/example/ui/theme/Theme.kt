package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = Color(0xFF8CF2FF),

    secondary = ElectricBlue,
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004C63),
    onSecondaryContainer = Color(0xFFBAEAFF),

    tertiary = EmeraldStatus,
    onTertiary = Color(0xFF003823),
    tertiaryContainer = Color(0xFF005235),
    onTertiaryContainer = Color(0xFF84F8C1),

    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,

    outline = DarkSurfaceBorder,
    outlineVariant = Color(0xFF1E293B),
    error = RoseError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
