package com.example.gymapp.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.gymapp.Haptics
import com.example.gymapp.TodayStats
import com.example.gymapp.WearSync
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
internal fun IdleScreen(onOpenHistory: () -> Unit) {
    val config = LocalConfiguration.current
    val isRound = config.isScreenRound
    val s = rememberScreenInfo()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today by WatchWearSync.todayStats.collectAsState()
    val history by WatchWearSync.workoutHistory.collectAsState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Round: no autoCentering — items stack from top so the arc cards land in
            // positions where onGloballyPositioned gives a sensible horizontal inset.
            // Top padding clears the TimeText overlay.
            // Square: standard autoCentering + horizontal padding.
            autoCentering = if (isRound) null else AutoCenteringParams(),
            contentPadding = if (isRound) {
                PaddingValues(top = 40.dp, bottom = 20.dp)
            } else {
                PaddingValues(horizontal = s.hPad)
            },
            verticalArrangement = Arrangement.spacedBy(s.spacing),
        ) {
            // ── Stats ──────────────────────────────────────────────────────────
            item {
                if (isRound) {
                    RoundStatsCard(today, s)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardColor)
                            .padding(vertical = if (s.isLarge) 10.dp else 8.dp),
                    ) {
                        StatTile(Icons.AutoMirrored.Rounded.DirectionsWalk, AccentColor, today?.steps ?: "—",     "Steps", Modifier.weight(1f), s)
                        StatTile(Icons.Rounded.LocalFireDepartment,          RestColor,   today?.calories ?: "—",  "Kcal",  Modifier.weight(1f), s)
                        StatTile(Icons.Rounded.MonitorHeart,                 HeartColor,  today?.heartRate ?: "—", "HR",    Modifier.weight(1f), s)
                    }
                }
            }

            // ── Start Workout ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.hPad)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(AccentBrightColor, AccentColor)))
                        .clickable {
                            Haptics.workoutStart(context)
                            scope.launch { WatchWearSync.sendStartWorkout(context) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(s.iconMd))
                        Text("Start Workout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = s.titleSp)
                    }
                }
            }

            // ── History ────────────────────────────────────────────────────────
            item {
                if (isRound) {
                    RoundHistoryCard(history, s, onClick = {
                        Haptics.repComplete(context)
                        onOpenHistory()
                    })
                } else {
                    Chip(
                        onClick = {
                            Haptics.repComplete(context)
                            onOpenHistory()
                        },
                        label = { Text("History", fontSize = s.titleSp) },
                        secondaryLabel = {
                            Text(
                                if (history.isEmpty()) "No workouts yet"
                                else "${history.size} workout${if (history.size == 1) "" else "s"}",
                                fontSize = s.bodySp,
                            )
                        },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Round-specific arc cards
//
// Both cards span the full display width so the circular bezel clips the outer
// corners into a natural arc. Each uses a gradient that blends into the black
// screen: stats fades downward, history fades upward.
//
// Content insets are measured at the CENTRE of each card (not the extreme edge)
// to give a usable content width while keeping tiles well clear of the bezel.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stats arc — flat top corners (bezel = arc), rounded bottom corners.
 * Gradient: solid [CardColor] at top → transparent at bottom.
 */
@Composable
private fun RoundStatsCard(stats: TodayStats?, s: ScreenInfo) {
    ArcCard(
        heightDp = if (s.isLarge) 62.dp else 54.dp,
        topCornersRounded = false,
        bottomCornersRounded = true,
        gradient = Brush.verticalGradient(listOf(CardColor, CardColor.copy(alpha = 0f))),
    ) { inset ->
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatTile(Icons.AutoMirrored.Rounded.DirectionsWalk, AccentColor, stats?.steps ?: "—",     "Steps", Modifier.weight(1f), s)
            StatTile(Icons.Rounded.LocalFireDepartment,          RestColor,   stats?.calories ?: "—",  "Kcal",  Modifier.weight(1f), s)
            StatTile(Icons.Rounded.MonitorHeart,                 HeartColor,  stats?.heartRate ?: "—", "HR",    Modifier.weight(1f), s)
        }
    }
}

/**
 * History arc — rounded top corners, flat bottom corners (bezel = arc).
 * Gradient: transparent at top → solid [CardColor] at bottom.
 */
@Composable
private fun RoundHistoryCard(
    history: List<WearSync.WorkoutHistoryEntry>,
    s: ScreenInfo,
    onClick: () -> Unit,
) {
    ArcCard(
        heightDp = if (s.isLarge) 62.dp else 54.dp,
        topCornersRounded = true,
        bottomCornersRounded = false,
        gradient = Brush.verticalGradient(listOf(CardColor.copy(alpha = 0f), CardColor)),
        onClick = onClick,
    ) { inset ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(GoodColor.copy(alpha = 0.18f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = GoodColor, modifier = Modifier.size(s.iconSm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("History", fontWeight = FontWeight.SemiBold, fontSize = s.titleSp)
                Text(
                    if (history.isEmpty()) "No workouts yet"
                    else "${history.size} workout${if (history.size == 1) "" else "s"}",
                    color = DimColor, fontSize = s.captionSp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DimColor, modifier = Modifier.size(s.iconSm))
        }
    }
}

/**
 * Arc-card primitive shared by [RoundStatsCard] and [RoundHistoryCard].
 *
 * Spans full width; the circular bezel clips the flat corners into an arc.
 * Inset is derived from the card's **centre** y-position: that's the point where
 * tiles/text actually live, so it gives the tightest safe margin without pushing
 * content behind the bezel at the top or bottom corners.
 */
@Composable
private fun ArcCard(
    heightDp: Dp,
    topCornersRounded: Boolean,
    bottomCornersRounded: Boolean,
    gradient: Brush,
    onClick: (() -> Unit)? = null,
    content: @Composable (inset: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenRadiusPx = with(density) { (config.screenWidthDp / 2f).dp.toPx() }

    // Conservative default — tightened after the first layout pass.
    var contentInset by remember { mutableStateOf(20.dp) }

    val topR = if (topCornersRounded) 14.dp else 0.dp
    val botR = if (bottomCornersRounded) 14.dp else 0.dp
    val shape = RoundedCornerShape(topStart = topR, topEnd = topR, bottomStart = botR, bottomEnd = botR)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .onGloballyPositioned { coords ->
                // Use the card's CENTRE y so the inset is representative of where
                // the content lives, not the extreme corner (which gives near-zero
                // safe width when the card grazes the screen edge).
                val cardMidY = coords.positionInRoot().y + coords.size.height / 2f
                val yFromCenter = abs(cardMidY - screenRadiusPx)
                val safeHalfPx = sqrt(max(0f, screenRadiusPx * screenRadiusPx - yFromCenter * yFromCenter))
                val requiredPx = (screenRadiusPx - safeHalfPx + with(density) { 8.dp.toPx() })
                    .coerceAtLeast(0f)
                contentInset = with(density) { requiredPx.toDp() }
            }
            .clip(shape)
            .background(gradient)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content(contentInset)
    }
}
