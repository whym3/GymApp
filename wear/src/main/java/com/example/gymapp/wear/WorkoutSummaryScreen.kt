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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.example.gymapp.WearSync
import com.example.gymapp.formatClock
import kotlinx.coroutines.launch

@Composable
internal fun WorkoutSummaryScreen(summary: WearSync.WorkoutSummary, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = rememberScreenInfo()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = s.hPad),
            verticalArrangement = Arrangement.spacedBy(s.spacing),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = AccentColor,
                        modifier = Modifier.size(if (s.isLarge) 32.dp else 26.dp),
                    )
                    Text(
                        "Workout Complete",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = s.titleSp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .padding(vertical = 4.dp),
                ) {
                    StatTile(Icons.Rounded.Timer, AccentColor, formatClock(summary.durationSec), "Duration", Modifier.weight(1f), s)
                    StatTile(Icons.Rounded.Layers, RestColor, summary.totalSets.toString(), "Sets", Modifier.weight(1f), s)
                    StatTile(Icons.Rounded.FitnessCenter, GoodColor, "${summary.totalVolumeKg} kg", "Volume", Modifier.weight(1f), s)
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(AccentBrightColor, AccentColor)))
                        .clickable {
                            Haptics.workoutComplete(context)
                            scope.launch {
                                WatchWearSync.sendSaveWorkout(context)
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Save Workout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = s.titleSp)
                }
            }
            item {
                Chip(
                    onClick = {
                        Haptics.repComplete(context)
                        scope.launch {
                            WatchWearSync.sendDiscardWorkout(context)
                            onDismiss()
                        }
                    },
                    label = { Text("Discard") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
