package com.ecodrive.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppFontScale
import com.ecodrive.app.domain.model.AppTheme

/**
 * Custom semantic colors for EcoDrive.
 */
@Immutable
data class EcoDriveColors(
    val scoreExcellent: Color,
    val scoreGood: Color,
    val scoreAverage: Color,
    val scorePoor: Color,
    val gaugeBlue: Color,
    val gaugeOrange: Color,
    val gaugePurple: Color,
    val gaugeGreen: Color,
    val cardBackground: Color,
    val cardBorder: Color,
)

val LocalEcoDriveColors = staticCompositionLocalOf {
    EcoDriveColors(
        scoreExcellent = ScoreExcellent,
        scoreGood = ScoreGood,
        scoreAverage = ScoreAverage,
        scorePoor = ScorePoor,
        gaugeBlue = GaugeBlue,
        gaugeOrange = GaugeOrange,
        gaugePurple = GaugePurple,
        gaugeGreen = GaugeGreen,
        cardBackground = DarkCard,
        cardBorder = DarkCardBorder,
    )
}

/**
 * CompositionLocal that exposes whether the current app theme is dark.
 * Consume via [LocalIsDarkTheme.current] in any composable.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

object EcoDriveTheme {
    val colors: EcoDriveColors
        @Composable
        get() = LocalEcoDriveColors.current
}

@Composable
private fun createAppColorScheme(
    darkTheme: Boolean,
    palette: AppColorPalette,
    dynamicColor: Boolean
): Pair<ColorScheme, EcoDriveColors> {
    val context = LocalContext.current

    val baseColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (palette) {
            AppColorPalette.ECO_GREEN -> if (darkTheme) {
                darkColorScheme(primary = EcoGreen, primaryContainer = EcoGreenDark, secondary = EcoTeal)
            } else {
                lightColorScheme(primary = EcoGreenDark, primaryContainer = EcoGreenLight, secondary = EcoTeal)
            }
            AppColorPalette.MIDNIGHT_BLUE -> if (darkTheme) {
                darkColorScheme(primary = MidnightBlue, primaryContainer = MidnightBlueDark, secondary = AccentBlue)
            } else {
                lightColorScheme(primary = MidnightBlueDark, primaryContainer = MidnightBlueLight, secondary = AccentBlue)
            }
            AppColorPalette.SOLAR_ORANGE -> if (darkTheme) {
                darkColorScheme(primary = SolarOrange, primaryContainer = SolarOrangeDark, secondary = AccentAmber)
            } else {
                lightColorScheme(primary = SolarOrangeDark, primaryContainer = SolarOrangeLight, secondary = AccentAmber)
            }
            AppColorPalette.DEEP_PURPLE -> if (darkTheme) {
                darkColorScheme(primary = DeepPurple, primaryContainer = DeepPurpleDark, secondary = EcoTeal)
            } else {
                lightColorScheme(primary = DeepPurpleDark, primaryContainer = DeepPurpleLight, secondary = EcoTeal)
            }
            AppColorPalette.OCEAN_TEAL -> if (darkTheme) {
                darkColorScheme(primary = OceanTeal, primaryContainer = OceanTealDark, secondary = AccentBlue)
            } else {
                lightColorScheme(primary = OceanTealDark, primaryContainer = OceanTealLight, secondary = AccentBlue)
            }
            AppColorPalette.CRIMSON_RED -> if (darkTheme) {
                darkColorScheme(primary = CrimsonRed, primaryContainer = CrimsonRedDark, secondary = AccentAmber)
            } else {
                lightColorScheme(primary = CrimsonRedDark, primaryContainer = CrimsonRedLight, secondary = AccentAmber)
            }
            AppColorPalette.DYNAMIC -> if (darkTheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context)
                else darkColorScheme(primary = EcoGreen)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context)
                else lightColorScheme(primary = EcoGreenDark)
            }
        }
    }

    // Overlay common neutral colors and semantic colors
    val finalColorScheme = if (darkTheme) {
        baseColorScheme.copy(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onBackground = DarkOnSurface,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            error = ErrorRed,
            outline = DarkCardBorder,
        )
    } else {
        baseColorScheme.copy(
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = LightOnSurface,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            error = ErrorRed,
            outline = LightCardBorder,
        )
    }

    val semanticColors = EcoDriveColors(
        scoreExcellent = ScoreExcellent,
        scoreGood = ScoreGood,
        scoreAverage = ScoreAverage,
        scorePoor = ScorePoor,
        gaugeBlue = GaugeBlue,
        gaugeOrange = GaugeOrange,
        gaugePurple = GaugePurple,
        gaugeGreen = GaugeGreen,
        cardBackground = if (darkTheme) DarkCard else LightCard,
        cardBorder = if (darkTheme) DarkCardBorder else LightCardBorder,
    )

    return finalColorScheme to semanticColors
}

@Composable
fun EcoDriveTheme(
    appTheme: AppTheme = AppTheme.DARK,
    appPalette: AppColorPalette = AppColorPalette.ECO_GREEN,
    appFontScale: AppFontScale = AppFontScale.MEDIUM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    val (colorScheme, semanticColors) = createAppColorScheme(
        darkTheme = darkTheme,
        palette = appPalette,
        dynamicColor = appPalette == AppColorPalette.DYNAMIC
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val baseDensity = LocalDensity.current
    val scaleFactor = when (appFontScale) {
        AppFontScale.SMALL -> 0.85f
        AppFontScale.MEDIUM -> 1.0f
        AppFontScale.LARGE -> 1.15f
    }
    val newDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * scaleFactor
    )

    CompositionLocalProvider(
        LocalEcoDriveColors provides semanticColors,
        LocalIsDarkTheme provides darkTheme,
        LocalDensity provides newDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EcoDriveTypography,
            content = content,
        )
    }
}
