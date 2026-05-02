package com.jadeai.solvertracker.ui.theme

import androidx.compose.ui.graphics.Color

val JellyBlue = Color(0xFFA8D8FF)
val JellyPurple = Color(0xFFD7B7FF)
val JellyPink = Color(0xFFFF9FD1)
val JellyGreen = Color(0xFFC9F3D7)
val JellyYellow = Color(0xFFFFF0B8)
val JellyOrange = Color(0xFFFFD0A6)

val JellyBackgroundGradientStart = Color(0xFFE0E5EC)
val JellyBackgroundGradientEnd = Color(0xFFE0E5EC)

val JellyCardBackground = Color(0xCCFFFFFF)
val JellyCardBorder = Color(0xB3FFFFFF)

val JellyTextDark = Color(0xFF4A5568)
val JellyTextMedium = Color(0xFF64748B)
val JellyTextLight = Color(0xFF8A98AA)

val White = Color(0xFFFFFFFF)
val Transparent = Color(0x00000000)

// Backwards-compatible semantic colors used by existing components.
// Keeping these avoids a broad refactor while screens transition to Jelly UI.
val StatusInProgress = JellyBlue
val StatusComplete = JellyGreen
val ProblemColor = JellyOrange
val SolutionColor = JellyGreen
