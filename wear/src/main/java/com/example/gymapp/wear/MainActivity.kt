package com.example.gymapp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import com.example.gymapp.WearSync

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

    val active  by WatchWearSync.activeWorkout.collectAsState()
    val summary by WatchWearSync.workoutSummary.collectAsState()
    val history by WatchWearSync.workoutHistory.collectAsState()
    val detail  by WatchWearSync.workoutDetail.collectAsState()

    var showHistory   by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<WearSync.WorkoutHistoryEntry?>(null) }

    LaunchedEffect(selectedEntry?.id) {
        selectedEntry?.let { WatchWearSync.sendRequestWorkoutDetail(context, it.id) }
    }
    // A session starting drops any in-progress history browsing
    LaunchedEffect(active != null) {
        if (active != null) {
            showHistory = false
            selectedEntry = null
            WatchWearSync.consumeWorkoutDetail()
        }
    }

    // Request the on-wrist heart rate permission once on launch
    val sensorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) sensorPermission.launch(Manifest.permission.BODY_SENSORS)
    }

    // Stream heart-rate sensor only while a set is actually running (saves battery)
    val running = active?.running == true
    LaunchedEffect(running) {
        if (running) WatchHeartRateMonitor.start(context) else WatchHeartRateMonitor.stop(context)
    }
    val onWristBpm by WatchHeartRateMonitor.bpm.collectAsState()
    LaunchedEffect(onWristBpm) {
        onWristBpm?.let { WatchWearSync.sendHeartRate(context, it) }
    }

    MaterialTheme {
        when {
            // Show recap even after active goes null — gives the wearer glanceable confirmation
            summary != null -> WorkoutSummaryScreen(
                summary = summary!!,
                onDismiss = { WatchWearSync.consumeWorkoutSummary() },
            )
            active != null -> ActiveWorkoutScreen(active!!, onWristBpm)
            selectedEntry != null -> HistoryDetailScreen(
                workout = detail?.takeIf { it.id == selectedEntry!!.id },
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
