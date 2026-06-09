package com.example.gymapp.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.gymapp.Haptics
import com.example.gymapp.SavedWorkout
import com.example.gymapp.SetType
import com.example.gymapp.WearSync
import com.example.gymapp.WorkoutExercise
import com.example.gymapp.formatDuration
import com.example.gymapp.formatVolume
import com.example.gymapp.formatWorkoutDate

@Composable
internal fun HistoryListScreen(
    entries: List<WearSync.WorkoutHistoryEntry>,
    onOpen: (WearSync.WorkoutHistoryEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
            item { ListHeader { Text("History") } }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "No workouts yet — finish a session and it'll show up here.",
                        color = DimColor,
                        textAlign = TextAlign.Center,
                        fontSize = s.bodySp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(entry, s, onClick = {
                        Haptics.repComplete(context)
                        onOpen(entry)
                    })
                }
            }
            item {
                Chip(
                    onClick = onBack,
                    icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                    label = { Text("Back") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: WearSync.WorkoutHistoryEntry,
    s: ScreenInfo,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Bold, fontSize = s.titleSp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatWorkoutDate(entry.dateMillis)} · ${formatDuration(entry.durationSec)} · ${formatVolume(entry.totalVolumeKg)}",
                    color = DimColor,
                    fontSize = s.bodySp,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = DimColor,
                modifier = Modifier.size(s.iconSm),
            )
        }
        if (entry.muscleGroups.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.muscleGroups.take(3).forEach { group ->
                    val col = muscleColor(group)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(col.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(group, color = col, fontSize = s.captionSp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryDetailScreen(workout: SavedWorkout?, onBack: () -> Unit) {
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
            if (workout == null) {
                item { ListHeader { Text("Loading…") } }
                item {
                    Text(
                        "Fetching from phone…",
                        color = DimColor,
                        textAlign = TextAlign.Center,
                        fontSize = s.bodySp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            workout.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (s.isLarge) 16.sp else 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(formatWorkoutDate(workout.dateMillis), color = DimColor, fontSize = s.bodySp)
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
                        StatTile(Icons.Rounded.Timer, AccentColor, formatDuration(workout.durationSec), "Duration", Modifier.weight(1f), s)
                        StatTile(Icons.Rounded.Layers, RestColor, "${workout.totalSets}", "Sets", Modifier.weight(1f), s)
                        StatTile(Icons.Rounded.FitnessCenter, GoodColor, formatVolume(workout.totalVolumeKg), "Volume", Modifier.weight(1f), s)
                    }
                }
                workout.avgHeartRate?.let { hr ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardColor)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MonitorHeart,
                                contentDescription = null,
                                tint = HeartColor,
                                modifier = Modifier.size(s.iconSm),
                            )
                            Text("Avg heart rate", color = DimColor, fontSize = s.bodySp, modifier = Modifier.weight(1f))
                            Text("$hr bpm", fontWeight = FontWeight.Bold, fontSize = s.titleSp)
                        }
                    }
                }
                item { ListHeader { Text("Exercises") } }
                workout.exercises.forEach { ex ->
                    item { HistoryExerciseCard(ex, s) }
                }
            }
            item {
                Chip(
                    onClick = onBack,
                    icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                    label = { Text("Back") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HistoryExerciseCard(ex: WorkoutExercise, s: ScreenInfo) {
    val col = muscleColor(ex.group)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(col.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = col, modifier = Modifier.size(s.iconSm))
            }
            Text(ex.name, fontWeight = FontWeight.SemiBold, fontSize = s.titleSp)
        }
        Spacer(Modifier.height(6.dp))
        var workingNum = 0
        ex.sets.forEach { set ->
            val isWarmup = set.type == SetType.WARMUP
            if (!isWarmup) workingNum++
            val badge = if (isWarmup) "W" else "$workingNum"
            val badgeColor = when (set.type) {
                SetType.WARMUP  -> RestColor
                SetType.FAILURE -> HeartColor
                else            -> DimColor
            }
            Text(
                "$badge.  ${if (set.weight.isBlank() && set.reps.isBlank()) "—" else "${set.weight.ifBlank { "0" }} kg × ${set.reps.ifBlank { "0" }}"}",
                color = badgeColor,
                fontSize = s.bodySp,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}
