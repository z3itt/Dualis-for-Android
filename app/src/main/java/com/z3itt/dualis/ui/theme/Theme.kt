package com.z3itt.dualis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = DualisOrange,
    onPrimary = Color.White,
    secondary = LightMuted,
    onSecondary = LightForeground,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightCard,
    onSurface = LightForeground,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightMutedText,
    outline = LightBorder,
    error = LightDestructive,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DualisOrange,
    onPrimary = Color.White,
    secondary = DarkMuted,
    onSecondary = DarkForeground,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkCard,
    onSurface = DarkForeground,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkMutedText,
    outline = DarkBorder,
    error = DarkDestructive,
    onError = Color.White,
)

@Composable
fun DualisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DualisTypography,
        shapes = DualisShapes,
        content = content,
    )
}
