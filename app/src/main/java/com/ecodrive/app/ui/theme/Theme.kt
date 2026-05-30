package com.ecodrive.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EcoGreen,
    onPrimary = DarkBackground,
    primaryContainer = EcoGreenDark,
    secondary = EcoTeal,
    onSecondary = DarkBackground,
    tertiary = AccentBlue,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
    outline = DarkCardBorder,
)

@Composable
fun EcoDriveTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme  // Always dark theme for auto dashboard feel

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EcoDriveTypography,
        content = content,
    )
}
