package com.example.gymapp.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * App palette. Neutral surface/text tokens switch between dark and light; the
 * accent and muscle-group colors stay constant. [dark] is Compose state, so any
 * composable reading a token below recomposes when the theme flips.
 */
object AppPalette {
    var dark by mutableStateOf(true)

    val bg          get() = if (dark) Color(0xFF0F0F13) else Color(0xFFF4F4F7)
    val bgElev      get() = if (dark) Color(0xFF15151C) else Color(0xFFFFFFFF)
    val card        get() = if (dark) Color(0xFF1A1A24) else Color(0xFFFFFFFF)
    val cardElev    get() = if (dark) Color(0xFF22222F) else Color(0xFFECECF1)
    val line        get() = if (dark) Color(0x12FFFFFF) else Color(0x14000000)
    val lineStrong  get() = if (dark) Color(0x1FFFFFFF) else Color(0x24000000)
    val text        get() = if (dark) Color(0xFFF4F4F7) else Color(0xFF14141A)
    val sub         get() = if (dark) Color(0xFF9C9CAC) else Color(0xFF5A5A68)
    val muted       get() = if (dark) Color(0xFF62626F) else Color(0xFF8C8C99)
    // Subtle inset fill (set badges, inputs, chips) — light tint in dark, dark tint in light.
    val subtleFill  get() = if (dark) Color(0x0DFFFFFF) else Color(0x0A000000)
}

// Neutral, theme-aware tokens
val BgColor: Color         get() = AppPalette.bg
val BgElevColor: Color     get() = AppPalette.bgElev
val CardColor: Color       get() = AppPalette.card
val CardElevColor: Color   get() = AppPalette.cardElev
val LineColor: Color       get() = AppPalette.line
val LineStrongColor: Color get() = AppPalette.lineStrong
val TextColor: Color       get() = AppPalette.text
val SubTextColor: Color    get() = AppPalette.sub
val MutedColor: Color      get() = AppPalette.muted
val SubtleFillColor: Color get() = AppPalette.subtleFill

// Constant brand / accent colors — electric blue (Whoop-style)
val AccentColor     = Color(0xFF4F6EF7)
val AccentSoftColor = Color(0x244F6EF7)
val GoodColor       = Color(0xFF4FCB6B)

/** Blue gradient for primary CTAs and avatars (looks good in light & dark). */
val AccentGradientColors = listOf(Color(0xFF6E8BFF), Color(0xFF4F6EF7), Color(0xFF3A53D8))

/** Subtle, theme-aware gradient for neutral cards (fades into the surface color). */
val cardGradientColors: List<Color> get() = listOf(CardElevColor, CardColor)

// Muscle group colors (constant in both themes)
val MuscleChest     = Color(0xFFF7564F)
val MuscleBack      = Color(0xFF4F8AF7)
val MuscleLegs      = Color(0xFF4FCB6B)
val MuscleShoulders = Color(0xFFA977F7)
val MuscleArms      = Color(0xFFF7944F)
val MuscleCore      = Color(0xFFF7D24F)
