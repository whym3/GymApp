package com.example.gymapp.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text

// Brand palette — mirrors phone app accent / semantic hues for a consistent look across both surfaces.
internal val AccentColor = Color(0xFF4F6EF7)
internal val AccentBrightColor = Color(0xFF6E8BFF)
internal val HeartColor = Color(0xFFF7564F)
internal val RestColor = Color(0xFFF7B23B)
internal val GoodColor = Color(0xFF4FCB6B)
internal val DimColor = Color(0xFF8A8A8A)
internal val CardColor = Color(0xFF1C1D22)

internal val muscleColors = mapOf(
    "Chest" to Color(0xFFF7564F),
    "Back" to Color(0xFF4F8AF7),
    "Legs" to Color(0xFF4FCB6B),
    "Shoulders" to Color(0xFFA977F7),
    "Arms" to Color(0xFFF7944F),
    "Core" to Color(0xFFF7D24F),
)

internal fun muscleColor(group: String) = muscleColors[group] ?: DimColor

/**
 * Screen-size breakpoints following the Wear OS adaptive-design guidelines:
 * - Default / small: < 225 dp (192 dp typical)
 * - Large: ≥ 225 dp — larger type, bigger icons, more padding (Tier 3 adaptive)
 */
internal data class ScreenInfo(
    val isLarge: Boolean,
    val hPad: Dp,
    val spacing: Dp,
    val timerSp: TextUnit,
    val titleSp: TextUnit,
    val bodySp: TextUnit,
    val captionSp: TextUnit,
    val iconSm: Dp,
    val iconMd: Dp,
)

@Composable
internal fun rememberScreenInfo(): ScreenInfo {
    val large = LocalConfiguration.current.screenWidthDp >= 225
    return ScreenInfo(
        isLarge   = large,
        hPad      = if (large) 16.dp  else 10.dp,
        spacing   = if (large) 8.dp   else 6.dp,
        timerSp   = if (large) 38.sp  else 32.sp,
        titleSp   = if (large) 15.sp  else 13.sp,
        bodySp    = if (large) 12.5.sp else 11.sp,
        captionSp = if (large) 10.5.sp else 9.sp,
        iconSm    = if (large) 16.dp  else 13.dp,
        iconMd    = if (large) 18.dp  else 15.dp,
    )
}

/** Shared stat column — icon, bold value, muted label. Used on idle, history detail and summary screens. */
@Composable
internal fun StatTile(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    s: ScreenInfo,
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(s.iconSm))
        Text(value, fontWeight = FontWeight.Bold, fontSize = s.titleSp, textAlign = TextAlign.Center)
        Text(label, color = DimColor, fontSize = s.captionSp)
    }
}
