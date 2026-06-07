package com.example.gymapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    MaterialTheme {
        val workout = active
        if (workout != null) ActiveWorkoutScreen(workout) else IdleScreen()
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
private fun ActiveWorkoutScreen(workout: WearSync.ActiveWorkoutSnapshot) {
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

@Composable
private fun StatRow(label: String, value: String) {
    Text("$label: $value", modifier = Modifier.padding(horizontal = 12.dp))
}
