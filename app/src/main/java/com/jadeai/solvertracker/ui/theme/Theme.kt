package com.jadeai.solvertracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = JellyPink,
    onPrimary = JellyTextDark,
    primaryContainer = JellyPink.copy(alpha = 0.72f),
    onPrimaryContainer = JellyTextDark,
    secondary = JellyPurple,
    onSecondary = JellyTextDark,
    secondaryContainer = JellyPurple.copy(alpha = 0.62f),
    onSecondaryContainer = JellyTextDark,
    tertiary = JellyBlue,
    onTertiary = JellyTextDark,
    tertiaryContainer = JellyBlue.copy(alpha = 0.68f),
    onTertiaryContainer = JellyTextDark,
    background = JellyBackgroundGradientStart,
    onBackground = JellyTextDark,
    surface = JellyCardBackground,
    onSurface = JellyTextDark,
    surfaceVariant = White,
    onSurfaceVariant = JellyTextMedium,
    outline = Color.White.copy(alpha = 0.76f),
    outlineVariant = Color.White.copy(alpha = 0.42f),
    error = Color(0xFFE05B88),
    errorContainer = Color(0xFFFFD7E6),
    onErrorContainer = JellyTextDark
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SolverTrackerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
