package com.example.gymapp.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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

    // Request the activity-recognition permission (needed for steps/calories) once on launch
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    // Stream heart-rate sensor only while a set is actually running (saves battery)
    val running = active?.running == true
    val inSession = active != null
    LaunchedEffect(running) {
        if (running) WatchHeartRateMonitor.start(context) else WatchHeartRateMonitor.stop(context)
    }
    // Relay readings to the phone at most once every 2s — the sensor emits ~1/s
    // and each relay is a radio wake-up on both devices.
    val onWristBpm by WatchHeartRateMonitor.bpm.collectAsState()
    var lastHrRelayMs by remember { mutableStateOf(0L) }
    LaunchedEffect(onWristBpm) {
        val bpm = onWristBpm ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (now - lastHrRelayMs >= 2_000L) {
            lastHrRelayMs = now
            WatchWearSync.sendHeartRate(context, bpm)
        }
    }

    // Tracked continuously (not just during a session) so a baseline is
    // already available the moment a workout starts.
    LaunchedEffect(Unit) { WatchActivityMonitor.start(context) }
    val watchSteps by WatchActivityMonitor.steps.collectAsState()
    val watchCalories by WatchActivityMonitor.calories.collectAsState()

    // While a session exists, stream steps/calories burned *this workout* as
    // the delta from the totals captured the moment it started. Keyed on the
    // session's existence, NOT the running flag — pausing must not drop the
    // baseline, or everything accumulated before the pause is lost on resume.
    var sessionBaseline by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    LaunchedEffect(inSession, watchSteps, watchCalories) {
        if (inSession && sessionBaseline == null) {
            val steps = watchSteps
            val cals = watchCalories
            if (steps != null && cals != null) sessionBaseline = steps to cals
        } else if (!inSession) {
            sessionBaseline = null
        }
    }
    LaunchedEffect(watchSteps, watchCalories, sessionBaseline) {
        val baseline = sessionBaseline ?: return@LaunchedEffect
        val steps = watchSteps ?: return@LaunchedEffect
        val cals = watchCalories ?: return@LaunchedEffect
        WatchWearSync.sendSessionActivity(
            context,
            (steps - baseline.first).coerceAtLeast(0L),
            (cals - baseline.second).coerceAtLeast(0.0),
        )
    }

    MaterialTheme {
        // Fade-through between top-level watch screens instead of hard cuts.
        // Recap stays visible even after active goes null — glanceable confirmation.
        val screenKey = when {
            summary != null -> WatchScreen.SUMMARY
            active != null -> WatchScreen.ACTIVE
            selectedEntry != null -> WatchScreen.DETAIL
            showHistory -> WatchScreen.HISTORY
            else -> WatchScreen.IDLE
        }
        AnimatedContent(
            targetState = screenKey,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "watchScreen",
        ) { key ->
            when (key) {
                WatchScreen.SUMMARY -> summary?.let {
                    WorkoutSummaryScreen(
                        summary = it,
                        onDismiss = { WatchWearSync.consumeWorkoutSummary() },
                    )
                }
                WatchScreen.ACTIVE -> active?.let { ActiveWorkoutScreen(it, onWristBpm) }
                WatchScreen.DETAIL -> selectedEntry?.let { entry ->
                    HistoryDetailScreen(
                        workout = detail?.takeIf { it.id == entry.id },
                        onBack = {
                            selectedEntry = null
                            WatchWearSync.consumeWorkoutDetail()
                        },
                    )
                }
                WatchScreen.HISTORY -> HistoryListScreen(
                    entries = history,
                    onOpen = { selectedEntry = it },
                    onBack = { showHistory = false },
                )
                WatchScreen.IDLE -> IdleScreen(onOpenHistory = { showHistory = true })
            }
        }
    }
}

private enum class WatchScreen { SUMMARY, ACTIVE, DETAIL, HISTORY, IDLE }
