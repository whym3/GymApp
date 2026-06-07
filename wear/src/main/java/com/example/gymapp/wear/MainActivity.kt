package com.example.gymapp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.Haptics
import com.example.gymapp.WearSync
import com.example.gymapp.formatClock
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The wear module doesn't share the phone app's Material theme, so these
// mirror its accent / heart-rate / muscle hues for a consistent brand feel.
private val AccentColor = Color(0xFF4F6EF7)
private val HeartColor = Color(0xFFF7564F)
private val RestColor = Color(0xFFF7B23B)
private val DimColor = Color(0xFF8A8A8A)

/**
 * Single screen synced with the phone over the Wearable Data Layer. Shows the
 * idle dashboard ([WatchWearSync.todayStats]) until a session starts, then
 * switches to a remote control mirroring [WatchWearSync.activeWorkout].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchWearSync.init(applicationContext)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current

    LaunchedEffect(Unit) { WatchWearSync.init(context) }
    val active by WatchWearSync.activeWorkout.collectAsState()
    val summary by WatchWearSync.workoutSummary.collectAsState()

    // On-wrist heart rate: request the sensor permission once, then stream
    // readings only while a set is actually running — registering a Health
    // Services callback raises the sensor's sampling rate and battery draw.
    val sensorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            sensorPermission.launch(Manifest.permission.BODY_SENSORS)
        }
    }
    val running = active?.running == true
    LaunchedEffect(running) {
        if (running) WatchHeartRateMonitor.start(context) else WatchHeartRateMonitor.stop(context)
    }
    val onWristBpm by WatchHeartRateMonitor.bpm.collectAsState()
    LaunchedEffect(onWristBpm) {
        onWristBpm?.let { WatchWearSync.sendHeartRate(context, it) }
    }

    MaterialTheme {
        val workout = active
        val recap = summary
        when {
            // Show the recap even once the session has ended and `active` has
            // gone null — it's the wearer's only glanceable confirmation that
            // the workout actually saved.
            recap != null -> WorkoutSummaryScreen(recap, onDismiss = { WatchWearSync.consumeWorkoutSummary() })
            workout != null -> ActiveWorkoutScreen(workout, onWristBpm)
            else -> IdleScreen()
        }
    }
}

@Composable
private fun IdleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val today by WatchWearSync.todayStats.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("GymLog") } }
        val stats = today
        if (stats == null) {
            // The watch hasn't received a snapshot yet — either the phone app
            // hasn't opened since this watch paired, or the two are out of range.
            item {
                Text(
                    "Waiting for phone…",
                    color = DimColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            item { StatRow(Icons.AutoMirrored.Rounded.DirectionsWalk, AccentColor, "Steps", stats.steps) }
            item { StatRow(Icons.Rounded.LocalFireDepartment, AccentColor, "Active", stats.calories) }
            item { StatRow(Icons.Rounded.MonitorHeart, HeartColor, "Heart rate", stats.heartRate) }
        }
        item {
            Chip(
                onClick = {
                    Haptics.workoutStart(context)
                    scope.launch { WatchWearSync.sendStartWorkout(context) }
                },
                label = { Text("Start Workout") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Glanceable recap shown the moment a session ends — the wearer's hands are
 * usually full putting equipment away, so this auto-dismisses back to the
 * idle dashboard after a few seconds (a "Done" chip lets them skip the wait).
 */
@Composable
private fun WorkoutSummaryScreen(summary: WearSync.WorkoutSummary, onDismiss: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(summary) {
        delay(6_000)
        onDismiss()
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(28.dp),
                )
                Text("Workout complete", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        item { StatRow(Icons.Rounded.Timer, AccentColor, "Duration", formatClock(summary.durationSec)) }
        item { StatRow(Icons.Rounded.FitnessCenter, AccentColor, "Sets", summary.totalSets.toString()) }
        item { StatRow(Icons.Rounded.LocalFireDepartment, AccentColor, "Volume", "${summary.totalVolumeKg} kg") }
        item {
            Chip(
                onClick = {
                    Haptics.repComplete(context)
                    onDismiss()
                },
                label = { Text("Done") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot, onWristBpm: Int?) {
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

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            // The timer is the single thing a lifter glances at most — give it
            // the most visual weight and center it so it reads at a glance.
            Text(
                formatClock(workout.elapsedSec),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            // Tappable when there's more than one exercise to pick from — opens
            // ExercisePickerScreen so the watch can target a different exercise.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (workout.exercises.size > 1) {
                            Modifier.clickable {
                                Haptics.repComplete(context)
                                pickingExercise = true
                            }
                        } else Modifier,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    workout.exerciseName,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(workout.setProgress, color = DimColor, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        workout.restSec?.let { rest ->
            item {
                Text(
                    "Rest  ${formatClock(rest)}",
                    color = RestColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
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
        if (!workout.running) {
            item {
                Text(
                    "Paused",
                    color = DimColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        onWristBpm?.let { bpm ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MonitorHeart,
                        contentDescription = null,
                        tint = HeartColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$bpm bpm", color = HeartColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
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
            )
        }
        item {
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
            )
        }
        item {
            Chip(
                onClick = {
                    Haptics.repComplete(context)
                    scope.launch { WatchWearSync.sendAddSet(context) }
                },
                label = { Text("Add set") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            // Long-press confirmation, not a tap, so a stray brush of the
            // wrist mid-lift can't accidentally complete the set. Built from
            // scratch (rather than wrapping Chip) because Chip installs its own
            // click handler — a second, outer combinedClickable never sees the
            // down event since the inner detector consumes it first.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colors.primary)
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
                Text("Hold to mark done", color = MaterialTheme.colors.onPrimary)
            }
        }
        item {
            Chip(
                onClick = {
                    Haptics.repComplete(context)
                    scope.launch { WatchWearSync.sendTogglePause(context) }
                },
                label = { Text(if (workout.running) "Pause" else "Resume") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = {
                    Haptics.workoutComplete(context)
                    scope.launch { WatchWearSync.sendFinishWorkout(context) }
                },
                label = { Text("Finish") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

/** A labeled value flanked by −/+ buttons — used to nudge the current set's weight and reps from the wrist. */
@Composable
private fun AdjustRow(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton(onClick = onDecrease) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = DimColor, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        CompactButton(onClick = onIncrease) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
        }
    }
}

/** Lets the wearer pick which exercise the remote control (mark-done / weight / reps / add-set) targets. */
@Composable
private fun ExercisePickerScreen(
    workout: WearSync.ActiveWorkoutSnapshot,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
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
            )
        }
    }
}

/**
 * Low-power layout shown while the wrist is down. No chips (the screen isn't
 * interactive in ambient), muted gray-on-black to limit burn-in, and everything
 * centered so the round display stays balanced — Wear OS repaints this roughly
 * once a minute via onUpdateAmbient.
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
                // Plain Icon (not the "♥" glyph) — emoji glyphs carry their own
                // embedded color and would render in saturated red here, which
                // is exactly the kind of burn-in risk this dim palette avoids.
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

@Composable
private fun StatRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = DimColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
