package com.example.gymapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.gymapp.TodayStats

/**
 * Phase 1 scaffold: a read-only glance of today's stats plus a "Start Workout"
 * stub. Data here is a placeholder — the next step wires this up to the phone
 * over the Wearable Data Layer (DataClient for stats, MessageClient for the
 * start-workout command and active-session mirroring).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    // Placeholder until phone sync (DataClient) is wired up.
    val today = TodayStats(steps = "—", calories = "—", heartRate = "—")

    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            item { ListHeader { Text("GymLog") } }
            item { StatRow("Steps", today.steps) }
            item { StatRow("Calories", today.calories) }
            item { StatRow("Heart rate", today.heartRate) }
            item {
                Chip(
                    onClick = { /* TODO: send "start workout" message to phone */ },
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
