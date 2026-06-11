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
import kotlinx.coroutines.delay

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

    // On-wrist sensor permissions. BODY_SENSORS_BACKGROUND is chained after the
    // foreground grant: workouts usually start from the phone while the watch
    // screen is off (activity paused), and starting the Health Services
    // exercise from there throws SecurityException without the background grant.
    val backgroundSensorPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val sensorPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) backgroundSensorPermission.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }
    LaunchedEffect(Unit) {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        when {
            !has(Manifest.permission.BODY_SENSORS) ->
                sensorPermission.launch(Manifest.permission.BODY_SENSORS)
            !has(Manifest.permission.BODY_SENSORS_BACKGROUND) ->
                backgroundSensorPermission.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }
    }

    // Request the activity-recognition permission (needed for steps/calories) once on launch
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    // Drive a Health Services *exercise* session in lockstep with the phone's
    // workout. ExerciseClient is the only live path for in-workout steps/kcal
    // (passive monitoring batches daily totals with unbounded latency, so its
    // values sit frozen mid-session) and it streams on-wrist HR too.
    val running = active?.running == true
    val inSession = active != null
    LaunchedEffect(inSession) {
        if (!inSession) {
            WatchExerciseMonitor.stop(context)
            return@LaunchedEffect
        }
        // Keep nudging while the session lasts: start() is a no-op once the
        // exercise is live, but a failed attempt (e.g. permission granted only
        // mid-session) resets itself and gets retried here.
        while (true) {
            WatchExerciseMonitor.start(context)
            delay(5_000L)
        }
    }
    LaunchedEffect(running, inSession) {
        if (inSession) {
            if (running) WatchExerciseMonitor.resume(context) else WatchExerciseMonitor.pause(context)
        }
    }

    // Relay HR to the phone at most once every 2s — each relay is a radio
    // wake-up on both devices.
    val onWristBpm by WatchExerciseMonitor.heartRate.collectAsState()
    var lastHrRelayMs by remember { mutableStateOf(0L) }
    LaunchedEffect(onWristBpm) {
        val bpm = onWristBpm ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (now - lastHrRelayMs >= 2_000L) {
            lastHrRelayMs = now
            WatchWearSync.sendHeartRate(context, bpm)
        }
    }

    // Daily totals: feeds the idle dashboard, and doubles as the in-session
    // steps fallback below.
    LaunchedEffect(Unit) { WatchActivityMonitor.start(context) }

    // Live session steps/kcal. Calories (and steps, where offered) come from
    // the exercise — cumulative since the session started, nothing to subtract.
    // Some watches (this one included) expose no step data type for exercises
    // at all, so steps fall back to a session delta over the passive daily
    // counter; while an exercise is live the sensor pipeline runs in
    // fast-flush mode, so that counter updates quickly too.
    val exerciseSteps by WatchExerciseMonitor.steps.collectAsState()
    val dailySteps by WatchActivityMonitor.steps.collectAsState()
    var stepsBaseline by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(inSession, dailySteps) {
        if (!inSession) stepsBaseline = null
        else if (stepsBaseline == null && dailySteps != null) stepsBaseline = dailySteps
    }
    val sessionSteps = exerciseSteps
        ?: stepsBaseline?.let { base -> dailySteps?.let { now -> (now - base).coerceAtLeast(0L) } }
    val sessionCalories by WatchExerciseMonitor.calories.collectAsState()
    LaunchedEffect(sessionSteps, sessionCalories) {
        if (inSession && (sessionSteps != null || sessionCalories != null)) {
            WatchWearSync.sendSessionActivity(context, sessionSteps ?: 0L, sessionCalories ?: 0.0)
        }
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
