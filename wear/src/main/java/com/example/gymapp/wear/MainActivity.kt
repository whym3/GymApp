package com.example.gymapp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.Haptics
import com.example.gymapp.WearSync
import com.example.gymapp.formatClock
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
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
        if (workout != null) ActiveWorkoutScreen(workout, onWristBpm) else IdleScreen()
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
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Chip(
                onClick = {
                    Haptics.repComplete(context)
                    scope.launch { WatchWearSync.sendMarkSetDone(context) }
                },
                label = { Text("Mark set done") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.padding(top = 6.dp),
            )
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
