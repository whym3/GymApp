package com.example.gymapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary          = AccentColor,
    onPrimary        = Color.White,
    secondary        = GoodColor,
    background        = Color(0xFF0F0F13),
    surface          = Color(0xFF1A1A24),
    surfaceVariant   = Color(0xFF22222F),
    onBackground     = Color(0xFFF4F4F7),
    onSurface        = Color(0xFFF4F4F7),
    onSurfaceVariant = Color(0xFF9C9CAC),
    outline          = Color(0x1FFFFFFF),
)

private val LightScheme = lightColorScheme(
    primary          = AccentColor,
    onPrimary        = Color.White,
    secondary        = GoodColor,
    background        = Color(0xFFF4F4F7),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFECECF1),
    onBackground     = Color(0xFF14141A),
    onSurface        = Color(0xFF14141A),
    onSurfaceVariant = Color(0xFF5A5A68),
    outline          = Color(0x24000000),
)

/**
 * @param dark whether to render the dark palette. The caller resolves the
 * user's Light/Dark/System preference into this boolean. We also push it into
 * [AppPalette] so the standalone color tokens (BgColor, CardColor, …) follow.
 */
@Composable
fun GymAppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    if (AppPalette.dark != dark) AppPalette.dark = dark
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography,
        content = content,
    )
}
