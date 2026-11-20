package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WewaDarkColorScheme = darkColorScheme(
    primary = WewaAmber,
    onPrimary = Color(0xFF1A1000),
    primaryContainer = WewaAmberContainer,
    onPrimaryContainer = WewaAmberBright,
    secondary = WewaAmberBright,
    onSecondary = Color(0xFF1E1200),
    secondaryContainer = WewaSurfaceElevated,
    onSecondaryContainer = WewaTextPrimary,
    tertiary = WewaGreen,
    onTertiary = Color(0xFF04210E),
    tertiaryContainer = WewaGreenContainer,
    onTertiaryContainer = WewaGreen,
    error = WewaRed,
    onError = Color.White,
    errorContainer = WewaRedContainer,
    onErrorContainer = WewaRed,
    background = WewaObsidian,
    onBackground = WewaTextPrimary,
    surface = WewaCharcoal,
    onSurface = WewaTextPrimary,
    surfaceVariant = WewaSurface,
    onSurfaceVariant = WewaTextSecondary,
    surfaceContainerLowest = WewaObsidian,
    surfaceContainerLow = WewaCharcoal,
    surfaceContainer = WewaSurface,
    surfaceContainerHigh = WewaSurfaceElevated,
    outline = WewaBorderSubtle,
    outlineVariant = Color(0xFF23211D)
)

val WewaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WewaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WewaDarkColorScheme,
        typography = WewaTypography,
        shapes = WewaShapes,
        content = content
    )
}
