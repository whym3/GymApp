package com.example.gymapp.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.gymapp.Haptics
import com.example.gymapp.SetType
import com.example.gymapp.WearSync
import com.example.gymapp.formatClock
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import kotlinx.coroutines.launch

@Composable
internal fun ActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot, onWristBpm: Int?) {
    AmbientAware { ambientState ->
        if (ambientState is AmbientState.Ambient) {
            AmbientActiveWorkoutScreen(workout, onWristBpm)
        } else {
            InteractiveActiveWorkoutScreen(workout, onWristBpm)
        }
    }
}

@Composable
private fun InteractiveActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot, onWristBpm: Int?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = rememberScreenInfo()
    val listState = rememberScalingLazyListState()
    var pickingExercise by remember { mutableStateOf(false) }

    if (pickingExercise) {
        ExercisePickerScreen(
            workout = workout,
            onPick = { index ->
                Haptics.repComplete(context)
                scope.launch { WatchWearSync.sendSelectExercise(context, index) }
                pickingExercise = false
            },
            onCancel = { pickingExercise = false },
        )
        return
    }

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
            // Hero timer card — the single thing a lifter glances at most
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardColor)
                        .padding(vertical = if (s.isLarge) 16.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formatClock(workout.elapsedSec),
                        fontSize = s.timerSp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    val statusColor = if (workout.running) GoodColor else DimColor
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (workout.running) "Running" else "Paused",
                            color = statusColor,
                            fontSize = s.captionSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Current exercise — tappable when there are multiple
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .then(
                            if (workout.exercises.size > 1) Modifier.clickable {
                                Haptics.repComplete(context)
                                pickingExercise = true
                            } else Modifier,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AccentColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AccentColor, modifier = Modifier.size(s.iconSm))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(workout.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = s.titleSp)
                        Text(workout.setProgress, color = DimColor, fontSize = s.bodySp)
                    }
                    if (workout.exercises.size > 1) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DimColor, modifier = Modifier.size(s.iconSm))
                    }
                }
            }

            // Rest timer — amber tint distinguishes it as a distinct mode
            workout.restSec?.let { rest ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RestColor.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Rest", color = RestColor, fontSize = s.captionSp, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatClock(rest),
                            color = RestColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (s.isLarge) 26.sp else 22.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompactChip(
                                onClick = {
                                    Haptics.repComplete(context)
                                    scope.launch { WatchWearSync.sendAddRest(context) }
                                },
                                label = { Text("+15s") },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.weight(1f),
                            )
                            CompactChip(
                                onClick = {
                                    Haptics.repComplete(context)
                                    scope.launch { WatchWearSync.sendSkipRest(context) }
                                },
                                label = { Text("Skip") },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Live heart rate pill
            onWristBpm?.let { bpm ->
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(HeartColor.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = HeartColor, modifier = Modifier.size(s.iconSm))
                            Spacer(Modifier.width(5.dp))
                            Text("$bpm bpm", color = HeartColor, fontWeight = FontWeight.SemiBold, fontSize = s.bodySp)
                        }
                    }
                }
            }

            // Weight / reps card with properly-sized ±  buttons (40–44 dp touch target)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Current set", color = DimColor, fontSize = s.captionSp, fontWeight = FontWeight.SemiBold)
                        val (typLabel, typColor) = when (workout.currentSetType) {
                            SetType.WARMUP  -> "Warmup" to RestColor
                            SetType.FAILURE -> "Failure" to HeartColor
                            SetType.NORMAL  -> null to null
                        }
                        if (typLabel != null && typColor != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(typColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(typLabel, color = typColor, fontSize = s.captionSp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    AdjustRow(
                        label = "Weight",
                        value = "${workout.currentWeight.ifBlank { "—" }} kg",
                        onDecrease = {
                            Haptics.repComplete(context)
                            scope.launch { WatchWearSync.sendAdjustWeight(context, -2.5) }
                        },
                        onIncrease = {
                            Haptics.repComplete(context)
                            scope.launch { WatchWearSync.sendAdjustWeight(context, 2.5) }
                        },
                        s = s,
                    )
                    AdjustRow(
                        label = "Reps",
                        value = workout.currentReps.ifBlank { "—" },
                        onDecrease = {
                            Haptics.repComplete(context)
                            scope.launch { WatchWearSync.sendAdjustReps(context, -1) }
                        },
                        onIncrease = {
                            Haptics.repComplete(context)
                            scope.launch { WatchWearSync.sendAdjustReps(context, 1) }
                        },
                        s = s,
                    )
                }
            }

            item {
                Chip(
                    onClick = {
                        Haptics.repComplete(context)
                        scope.launch { WatchWearSync.sendAddSet(context) }
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    label = { Text("Add set") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Long-press to avoid accidental set completion mid-lift; gradient matches idle hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(AccentBrightColor, AccentColor)))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                Haptics.repComplete(context)
                                scope.launch { WatchWearSync.sendMarkSetDone(context) }
                            },
                            onLongClickLabel = "Mark set done",
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Hold to mark done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = s.titleSp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        onClick = {
                            Haptics.repComplete(context)
                            scope.launch { WatchWearSync.sendTogglePause(context) }
                        },
                        icon = {
                            Icon(
                                if (workout.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                            )
                        },
                        label = { Text(if (workout.running) "Pause" else "Resume") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        onClick = {
                            Haptics.workoutComplete(context)
                            scope.launch { WatchWearSync.sendFinishWorkout(context) }
                        },
                        icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                        label = { Text("Finish") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * ± buttons for nudging weight / reps. Custom circular Box instead of CompactButton to
 * guarantee a 40–44 dp touch target (CompactButton's 32 dp height is below the Wear OS minimum).
 */
@Composable
private fun AdjustRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    s: ScreenInfo,
) {
    val btnSize = if (s.isLarge) 44.dp else 40.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(btnSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onDecrease),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(20.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = DimColor, fontSize = s.captionSp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = s.titleSp)
        }
        Box(
            modifier = Modifier
                .size(btnSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onIncrease),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label", modifier = Modifier.size(20.dp))
        }
    }
}

/** Lets the wearer pick which exercise the remote-control targets. */
@Composable
internal fun ExercisePickerScreen(
    workout: WearSync.ActiveWorkoutSnapshot,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { ListHeader { Text("Switch exercise") } }
            workout.exercises.forEachIndexed { index, name ->
                item {
                    Chip(
                        onClick = { onPick(index) },
                        label = { Text(name) },
                        colors = if (index == workout.currentExerciseIndex) {
                            ChipDefaults.primaryChipColors()
                        } else {
                            ChipDefaults.secondaryChipColors()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = onCancel,
                    icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                    label = { Text("Back") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Low-power ambient layout. No chips (non-interactive in ambient), muted gray-on-black
 * to limit OLED burn-in. Wear OS repaints roughly once per minute via onUpdateAmbient.
 */
@Composable
private fun AmbientActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot, onWristBpm: Int?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            formatClock(workout.elapsedSec),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = DimColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(workout.exerciseName, color = DimColor, textAlign = TextAlign.Center)
        Text(workout.setProgress, color = DimColor, textAlign = TextAlign.Center)
        workout.restSec?.let { rest ->
            Text("Rest ${formatClock(rest)}", color = DimColor, textAlign = TextAlign.Center)
        }
        onWristBpm?.let { bpm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.MonitorHeart,
                    contentDescription = null,
                    tint = DimColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("$bpm bpm", color = DimColor, textAlign = TextAlign.Center)
            }
        }
        if (!workout.running) {
            Text("Paused", color = DimColor, textAlign = TextAlign.Center)
        }
    }
}
