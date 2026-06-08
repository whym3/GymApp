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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.Haptics
import com.example.gymapp.SavedWorkout
import com.example.gymapp.WearSync
import com.example.gymapp.WorkoutExercise
import com.example.gymapp.formatClock
import com.example.gymapp.formatDuration
import com.example.gymapp.formatVolume
import com.example.gymapp.formatWorkoutDate
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The wear module doesn't share the phone app's Material theme, so these
// mirror its accent / heart-rate / muscle hues for a consistent brand feel.
private val AccentColor = Color(0xFF4F6EF7)
private val AccentBrightColor = Color(0xFF6E8BFF)
private val HeartColor = Color(0xFFF7564F)
private val RestColor = Color(0xFFF7B23B)
private val GoodColor = Color(0xFF4FCB6B)
private val DimColor = Color(0xFF8A8A8A)
private val CardColor = Color(0xFF1C1D22)

private val muscleColors = mapOf(
    "Chest" to Color(0xFFF7564F),
    "Back" to Color(0xFF4F8AF7),
    "Legs" to Color(0xFF4FCB6B),
    "Shoulders" to Color(0xFFA977F7),
    "Arms" to Color(0xFFF7944F),
    "Core" to Color(0xFFF7D24F),
)
private fun muscleColor(group: String) = muscleColors[group] ?: DimColor

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
    val history by WatchWearSync.workoutHistory.collectAsState()
    val detail by WatchWearSync.workoutDetail.collectAsState()

    // Idle-dashboard browsing state — History list, then optionally a tapped
    // entry's detail (fetched on demand from the phone, see LaunchedEffect below).
    var showHistory by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<WearSync.WorkoutHistoryEntry?>(null) }
    LaunchedEffect(selectedEntry?.id) {
        selectedEntry?.let { WatchWearSync.sendRequestWorkoutDetail(context, it.id) }
    }
    // A session starting takes over the screen — drop any in-progress browsing
    // so the wearer doesn't land back in a stale history view once it ends.
    LaunchedEffect(active != null) {
        if (active != null) {
            showHistory = false
            selectedEntry = null
            WatchWearSync.consumeWorkoutDetail()
        }
    }

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
        val entry = selectedEntry
        when {
            // Show the recap even once the session has ended and `active` has
            // gone null — it's the wearer's only glanceable confirmation that
            // the workout actually saved.
            recap != null -> WorkoutSummaryScreen(recap, onDismiss = { WatchWearSync.consumeWorkoutSummary() })
            workout != null -> ActiveWorkoutScreen(workout, onWristBpm)
            entry != null -> HistoryDetailScreen(
                workout = detail?.takeIf { it.id == entry.id },
                onBack = {
                    selectedEntry = null
                    WatchWearSync.consumeWorkoutDetail()
                },
            )
            showHistory -> HistoryListScreen(
                entries = history,
                onOpen = { selectedEntry = it },
                onBack = { showHistory = false },
            )
            else -> IdleScreen(onOpenHistory = { showHistory = true })
        }
    }
}

/**
 * Dashboard shown while idle — a glanceable recap of today plus quick entry
 * points into starting a session or browsing past ones. Mirrors the phone
 * `HomeScreen`'s language (gradient CTA, tonal stat cards, muscle-group
 * accents) scaled down for a round, one-handed wrist screen.
 */
@Composable
private fun IdleScreen(onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val today by WatchWearSync.todayStats.collectAsState()
    val history by WatchWearSync.workoutHistory.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ListHeader { Text("GymLog") } }
        item {
            // Gradient hero CTA — the one thing a wearer most wants to tap
            // before a session, given the most visual weight on the dashboard.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(AccentBrightColor, AccentColor)))
                    .clickable {
                        Haptics.workoutStart(context)
                        scope.launch { WatchWearSync.sendStartWorkout(context) }
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Text("Start Workout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .padding(vertical = 4.dp),
                ) {
                    StatTile(Icons.AutoMirrored.Rounded.DirectionsWalk, AccentColor, "Steps", stats.steps)
                    StatTile(Icons.Rounded.LocalFireDepartment, RestColor, "Active kcal", stats.calories)
                    StatTile(Icons.Rounded.MonitorHeart, HeartColor, "Heart rate", stats.heartRate)
                }
            }
        }
        item {
            // History entry point — count gives the wearer a reason to tap in,
            // chevron signals it opens a browsable list (mirrors phone HistoryCard).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .clickable {
                        Haptics.repComplete(context)
                        onOpenHistory()
                    }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(GoodColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = GoodColor, modifier = Modifier.size(17.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("History", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (history.isEmpty()) "No workouts yet" else "${history.size} workout${if (history.size == 1) "" else "s"}",
                        color = DimColor, fontSize = 11.5.sp,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DimColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A stat row inside the dashboard's tonal "Today" card — icon chip + label + value, mirroring the phone's detail-stat language. */
@Composable
private fun StatTile(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Text(label, color = DimColor, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * Browsable list of past workouts, mirroring the phone `HistoryScreen`'s card
 * language (title, date • duration • volume, muscle-group tags, chevron) in a
 * scrollable column sized for the wrist.
 */
@Composable
private fun HistoryListScreen(
    entries: List<WearSync.WorkoutHistoryEntry>,
    onOpen: (WearSync.WorkoutHistoryEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ListHeader { Text("History") } }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No workouts yet — finish a session and it'll show up here.",
                    color = DimColor,
                    textAlign = TextAlign.Center,
                    fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                HistoryEntryCard(entry, onClick = {
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
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: WearSync.WorkoutHistoryEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${formatWorkoutDate(entry.dateMillis)} · ${formatDuration(entry.durationSec)} · ${formatVolume(entry.totalVolumeKg)}",
                    color = DimColor, fontSize = 11.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DimColor, modifier = Modifier.size(16.dp))
        }
        if (entry.muscleGroups.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                entry.muscleGroups.take(3).forEach { group ->
                    val col = muscleColor(group)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(col.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(group, color = col, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Full breakdown of one saved workout — stats, optional avg heart rate, and
 * every exercise's sets — fetched on demand from the phone (see
 * [WatchWearSync.sendRequestWorkoutDetail]) and mirroring the phone
 * `WorkoutDetailScreen`'s layout.
 */
@Composable
private fun HistoryDetailScreen(workout: SavedWorkout?, onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (workout == null) {
            item { ListHeader { Text("Loading…") } }
            item {
                Text(
                    "Fetching from phone…",
                    color = DimColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        } else {
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(workout.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text(formatWorkoutDate(workout.dateMillis), color = DimColor, fontSize = 11.5.sp)
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
                    DetailStatTile(Icons.Rounded.Timer, AccentColor, formatDuration(workout.durationSec), "Duration", Modifier.weight(1f))
                    DetailStatTile(Icons.Rounded.Layers, RestColor, "${workout.totalSets}", "Sets", Modifier.weight(1f))
                    DetailStatTile(Icons.Rounded.FitnessCenter, GoodColor, formatVolume(workout.totalVolumeKg), "Volume", Modifier.weight(1f))
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
                        Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = HeartColor, modifier = Modifier.size(15.dp))
                        Text("Avg heart rate", color = DimColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("$hr bpm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            item { ListHeader { Text("Exercises") } }
            workout.exercises.forEach { ex ->
                item { HistoryExerciseCard(ex) }
            }
        }
        item {
            Chip(
                onClick = onBack,
                icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                label = { Text("Back") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
private fun DetailStatTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, color = DimColor, fontSize = 9.5.sp)
    }
}

@Composable
private fun HistoryExerciseCard(ex: WorkoutExercise) {
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
                modifier = Modifier.size(26.dp).background(col.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = col, modifier = Modifier.size(14.dp))
            }
            Text(ex.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        ex.sets.forEachIndexed { i, s ->
            Text(
                "${i + 1}.  ${if (s.weight.isBlank() && s.reps.isBlank()) "—" else "${s.weight.ifBlank { "0" }} kg × ${s.reps.ifBlank { "0" }}"}",
                color = DimColor, fontSize = 11.5.sp,
                modifier = Modifier.padding(vertical = 1.dp),
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Hero timer card — the single thing a lifter glances at most, given
            // the most visual weight (mirrors the dashboard's tonal hero cards).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardColor)
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatClock(workout.elapsedSec),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                val statusColor = if (workout.running) GoodColor else DimColor
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (workout.running) "Running" else "Paused",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item {
            // Exercise card — tappable when there's more than one to pick from,
            // opens ExercisePickerScreen so the watch can target a different one.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .then(
                        if (workout.exercises.size > 1) {
                            Modifier.clickable {
                                Haptics.repComplete(context)
                                pickingExercise = true
                            }
                        } else Modifier,
                    )
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(AccentColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(workout.exerciseName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(workout.setProgress, color = DimColor, fontSize = 11.5.sp)
                }
                if (workout.exercises.size > 1) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DimColor, modifier = Modifier.size(16.dp))
                }
            }
        }
        workout.restSec?.let { rest ->
            item {
                // Accent-tinted card sets the rest period apart as a distinct mode.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RestColor.copy(alpha = 0.14f))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Rest", color = RestColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatClock(rest), color = RestColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
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
        onWristBpm?.let { bpm ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HeartColor.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = HeartColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("$bpm bpm", color = HeartColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            // Current-set card — bundles the weight/reps adjusters under one tonal roof.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "Current set",
                    color = DimColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 2.dp),
                )
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
        item {
            // Long-press confirmation, not a tap, so a stray brush of the
            // wrist mid-lift can't accidentally complete the set. Built from
            // scratch (rather than wrapping Chip) because Chip installs its own
            // click handler — a second, outer combinedClickable never sees the
            // down event since the inner detector consumes it first. Gradient
            // mirrors the dashboard's "Start Workout" hero so the wearer's most
            // decisive in-session action reads with the same visual weight.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                Text("Hold to mark done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    icon = { Icon(if (workout.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null) },
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

/** A labeled value flanked by −/+ buttons — used to nudge the current set's weight and reps from the wrist. */
@Composable
private fun AdjustRow(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp, vertical = 2.dp),
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
