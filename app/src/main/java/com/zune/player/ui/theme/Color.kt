package com.zune.player.ui.theme

import androidx.compose.ui.graphics.Color

val ZuneBackground = Color(0xFF000000)
val ZuneTextPrimary = Color(0xFFFFFFFF)
val ZuneTextSecondary = Color(0xFFAAAAAA)

// Typical Zune / Metro Accent Colors.
val ZuneAccent = Color(0xFF808080) // Grey
val ZuneTileBackground = Color(0xFF1E1E1E)
val ZuneTileAccent = ZuneAccent

// High-fidelity Windows 7 Start Orb Blue and Gradients for Aero
val AeroBlueOrbAccentColor = Color(0xFF0083D7) // Premium Start Orb Blue

val AeroBlueOrbGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(
        Color(0xFF5BE5FF), // Radiant light cyan/teal glow
        Color(0xFF0083D7), // Premium Start Orb Blue
        Color(0xFF003D7C)  // Deep cobalt/navy
    )
)

val AeroBlueOrbGradientHorizontal = androidx.compose.ui.graphics.Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF5BE5FF),
        Color(0xFF0083D7),
        Color(0xFF003D7C)
    )
)

