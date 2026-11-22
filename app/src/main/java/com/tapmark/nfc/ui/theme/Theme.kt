package com.tapmark.nfc.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TapmarkLightColorScheme = lightColorScheme(
    primary = CopperTerracotta,
    onPrimary = Color.White,
    primaryContainer = CopperSoftTint,
    onPrimaryContainer = CopperDarkEdge,
    secondary = SecondaryGraphite,
    onSecondary = Color.White,
    secondaryContainer = RecessedSurface,
    onSecondaryContainer = PrimaryInk,
    tertiary = StatusSage,
    onTertiary = Color.White,
    tertiaryContainer = StatusSageTint,
    onTertiaryContainer = StatusSage,
    background = PaperCanvas,
    onBackground = PrimaryInk,
    surface = CardSurface,
    onSurface = PrimaryInk,
    surfaceVariant = RecessedSurface,
    onSurfaceVariant = SecondaryGraphite,
    surfaceContainerLowest = CardSurface,
    surfaceContainerLow = BlueprintSurface,
    surfaceContainer = RecessedSurface,
    surfaceContainerHigh = Color(0xFFE6E0D5),
    surfaceContainerHighest = DividerHairline,
    outline = DividerHairline,
    outlineVariant = DividerHairline,
    error = StatusCrimson,
    onError = Color.White,
    errorContainer = StatusCrimsonTint,
    onErrorContainer = StatusCrimson
)

@Composable
fun TapmarkTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = TapmarkLightColorScheme,
        typography = TapmarkTypography,
        content = content
    )
}
