package com.example.gymapp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.TodayStats
import com.example.gymapp.WearSync
import com.example.gymapp.formatClock
import com.google.android.horologist.compose.ambient.AmbientAware
import com.google.android.horologist.compose.ambient.AmbientState
import kotlinx.coroutines.launch

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
    val stats = today ?: TodayStats(steps = "—", calories = "—", heartRate = "—")

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("GymLog") } }
        item { StatRow("Steps", stats.steps) }
        item { StatRow("Calories", stats.calories) }
        item { StatRow("Heart rate", stats.heartRate) }
        item {
            Chip(
                onClick = { scope.launch { WatchWearSync.sendStartWorkout(context) } },
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                formatClock(workout.elapsedSec),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        item { Text(workout.exerciseName) }
        item { Text(workout.setProgress) }
        workout.restSec?.let { rest ->
            item { Text("Rest: ${formatClock(rest)}") }
        }
        onWristBpm?.let { bpm ->
            item { Text("♥ $bpm bpm") }
        }
        item {
            Chip(
                onClick = { scope.launch { WatchWearSync.sendMarkSetDone(context) } },
                label = { Text("Mark set done") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = { scope.launch { WatchWearSync.sendTogglePause(context) } },
                label = { Text(if (workout.running) "Pause" else "Resume") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = { scope.launch { WatchWearSync.sendFinishWorkout(context) } },
                label = { Text("Finish") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

/**
 * Low-power layout shown while the wrist is down. No chips (the screen isn't
 * interactive in ambient) and muted gray text on black to limit burn-in —
 * Wear OS repaints this roughly once a minute via onUpdateAmbient.
 */
@Composable
private fun AmbientActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot, onWristBpm: Int?) {
    val dimColor = Color(0xFF8A8A8A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            formatClock(workout.elapsedSec),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = dimColor,
        )
        Text(workout.exerciseName, color = dimColor)
        Text(workout.setProgress, color = dimColor)
        workout.restSec?.let { rest ->
            Text("Rest: ${formatClock(rest)}", color = dimColor)
        }
        onWristBpm?.let { bpm ->
            Text("♥ $bpm bpm", color = dimColor)
        }
        if (!workout.running) {
            Text("Paused", color = dimColor)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Text("$label: $value", modifier = Modifier.padding(horizontal = 12.dp))
}
