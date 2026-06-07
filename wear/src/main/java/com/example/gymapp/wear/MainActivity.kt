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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.TodayStats
import kotlinx.coroutines.launch

/**
 * Glance screen synced with the phone over the Wearable Data Layer:
 * [WatchWearSync.todayStats] mirrors the phone's dashboard (pushed via
 * DataClient whenever it refreshes), and the "Start Workout" chip messages
 * the phone to begin an empty session.
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { WatchWearSync.init(context) }
    val today by WatchWearSync.todayStats.collectAsState()
    val stats = today ?: TodayStats(steps = "—", calories = "—", heartRate = "—")

    MaterialTheme {
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
}

@Composable
private fun StatRow(label: String, value: String) {
    Text("$label: $value", modifier = Modifier.padding(horizontal = 12.dp))
}
