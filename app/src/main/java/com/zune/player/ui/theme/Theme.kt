package com.zune.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// The Zune/Metro UI is predominantly dark. We don't use shapes (everything is square).
// We'll map the standard Material colors strictly to Metro UI colors to avoid standard shadows/elevations looking weird.

private val ZuneColorPalette = darkColors(
    primary = ZuneAccent,
    primaryVariant = ZuneAccent,
    secondary = ZuneAccent,
    background = ZuneBackground,
    surface = ZuneBackground,
    onPrimary = ZuneTextPrimary,
    onSecondary = ZuneTextPrimary,
    onBackground = ZuneTextPrimary,
    onSurface = ZuneTextPrimary,
)

val LocalZuneAccent = compositionLocalOf { ZuneAccent }

@Composable
fun ZuneTheme(
    dynamicAccent: Color = ZuneAccent,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    
    val dynamicPalette = darkColors(
        primary = dynamicAccent,
        primaryVariant = dynamicAccent,
        secondary = dynamicAccent,
        background = ZuneBackground,
        surface = ZuneBackground,
        onPrimary = ZuneTextPrimary,
        onSecondary = ZuneTextPrimary,
        onBackground = ZuneTextPrimary,
        onSurface = ZuneTextPrimary,
    )

    CompositionLocalProvider(LocalZuneAccent provides dynamicAccent) {
        MaterialTheme(
            colors = dynamicPalette,
            typography = ZuneTypography,
            content = content
        )
    }
}
