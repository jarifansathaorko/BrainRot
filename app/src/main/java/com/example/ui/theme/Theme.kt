package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SleekColorScheme = lightColorScheme(
    primary = SleekPrimary,
    onPrimary = SleekOnPrimary,
    primaryContainer = SleekPrimaryContainer,
    onPrimaryContainer = SleekOnPrimaryContainer,
    secondary = SleekTextSecondary,
    onSecondary = SleekOnPrimary,
    secondaryContainer = SleekCardSurfaceElevated,
    onSecondaryContainer = SleekPillText,
    tertiary = SleekGold,
    onTertiary = SleekOnPrimary,
    background = SleekBg,
    onBackground = SleekTextPrimary,
    surface = SleekCardSurface,
    onSurface = SleekTextPrimary,
    surfaceVariant = SleekCardSurfaceSecondary,
    onSurfaceVariant = SleekTextSecondary,
    outline = SleekBorderDark,
    outlineVariant = SleekBorder
)

@Composable
fun ScrollyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SleekColorScheme,
        typography = Typography,
        content = content
    )
}
