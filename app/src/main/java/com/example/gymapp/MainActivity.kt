package com.example.gymapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.health.connect.client.PermissionController
import com.example.gymapp.ui.theme.AppPalette
import com.example.gymapp.ui.theme.BgColor
import com.example.gymapp.ui.theme.GymAppTheme
import com.example.gymapp.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale

enum class Screen {
    ONBOARDING, HOME, WORKOUT_LANDING, CREATE_ROUTINE, EDIT_ROUTINE, TEMPLATE_DETAIL,
    ACTIVE_WORKOUT, SUMMARY, HISTORY, WORKOUT_DETAIL, PROGRESS, PROFILE, HEART_RATE, STEPS
}

/** Coarse navigation depth, used to pick a forward (drill-in) vs back transition. */
private fun navDepth(s: Screen): Int = when (s) {
    Screen.ONBOARDING -> 0
    Screen.HOME, Screen.WORKOUT_LANDING, Screen.HISTORY, Screen.PROGRESS -> 1
    Screen.TEMPLATE_DETAIL, Screen.WORKOUT_DETAIL, Screen.PROFILE, Screen.HEART_RATE,
    Screen.STEPS, Screen.CREATE_ROUTINE, Screen.EDIT_ROUTINE, Screen.ACTIVE_WORKOUT -> 2
    Screen.SUMMARY -> 3
}

/**
 * Direction-aware screen transitions: session screens rise in like a modal,
 * drill-ins push from the right, going back pops to the right, and sibling
 * tabs fade through with a whisper of scale. All springs, never tweens.
 */
private fun AnimatedContentTransitionScope<Screen>.screenTransition(): ContentTransform {
    val toModal = targetState == Screen.ACTIVE_WORKOUT || targetState == Screen.SUMMARY
    val fromModal = initialState == Screen.ACTIVE_WORKOUT || initialState == Screen.SUMMARY
    val toDepth = navDepth(targetState)
    val fromDepth = navDepth(initialState)
    return when {
        toModal -> (slideInVertically(Motion.spatialOffset) { it / 5 } + fadeIn(Motion.effectsFloat))
            .togetherWith(fadeOut(Motion.effectsFloat))
        fromModal -> fadeIn(Motion.effectsFloat)
            .togetherWith(slideOutVertically(Motion.spatialOffset) { it / 5 } + fadeOut(Motion.effectsFloat))
        toDepth > fromDepth -> (slideInHorizontally(Motion.spatialOffset) { it / 4 } + fadeIn(Motion.effectsFloat))
            .togetherWith(slideOutHorizontally(Motion.spatialOffset) { -it / 6 } + fadeOut(Motion.effectsFloat))
        toDepth < fromDepth -> (slideInHorizontally(Motion.spatialOffset) { -it / 6 } + fadeIn(Motion.effectsFloat))
            .togetherWith(slideOutHorizontally(Motion.spatialOffset) { it / 4 } + fadeOut(Motion.effectsFloat))
        else -> (fadeIn(Motion.effectsFloat) + scaleIn(Motion.spatialFloat, initialScale = 0.96f))
            .togetherWith(fadeOut(Motion.effectsFloat))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserStore.init(applicationContext)
        ThemeStore.init(applicationContext)
        PhoneWearSync.init(applicationContext)
        WorkoutRepository.init(applicationContext)
        RoutineRepository.init(applicationContext)
        // Seed the palette before the first composition so frame one already has
        // the right theme; later flips are applied by GymAppTheme's SideEffect.
        AppPalette.dark = when (ThemeStore.mode) {
            ThemeStore.Mode.LIGHT -> false
            ThemeStore.Mode.DARK -> true
            ThemeStore.Mode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        enableEdgeToEdge()
        setContent {
            val dark = when (ThemeStore.mode) {
                ThemeStore.Mode.LIGHT -> false
                ThemeStore.Mode.DARK -> true
                ThemeStore.Mode.SYSTEM -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                // Dark status-bar icons on the light theme, light icons on dark.
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
            GymAppTheme(dark = dark) { GymApp() }
        }
    }
}

private fun newSessionExercise(lib: ExerciseLibraryItem): WorkoutExercise =
    WorkoutExercise(
        id = "ex-${lib.name}-${System.nanoTime()}",
        name = lib.name, group = lib.group, equip = lib.equip, open = true,
        sets = listOf(SetData("—", "", "", false)),
    )

/** First exercise/set that isn't marked done yet — what the watch remote calls "current". */
private fun firstUndoneSet(exercises: List<WorkoutExercise>): Pair<Int, Int>? {
    for (ei in exercises.indices) {
        val si = exercises[ei].sets.indexOfFirst { !it.done }
        if (si >= 0) return ei to si
    }
    return null
}

/** Renders a watch weight nudge (kg, possibly fractional) the way the phone's text fields would: no trailing ".0". */
private fun formatWeightKg(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

private fun activeWorkoutLabel(exercises: List<WorkoutExercise>): Pair<String, String> {
    val current = firstUndoneSet(exercises)
    return if (current != null) {
        val (ei, si) = current
        exercises[ei].name to "Set ${si + 1} of ${exercises[ei].sets.size}"
    } else {
        (exercises.lastOrNull()?.name ?: "—") to "All sets done"
    }
}

private fun repeatSessionExercise(ex: WorkoutExercise): WorkoutExercise =
    WorkoutExercise(
        id = "ex-${ex.name}-${System.nanoTime()}",
        name = ex.name, group = ex.group, equip = ex.equip, open = true,
        sets = ex.sets.map { prior ->
            val prev = if (prior.weight.isNotBlank() && prior.reps.isNotBlank())
                "${prior.weight} kg × ${prior.reps}" else "—"
            SetData(prev = prev, weight = "", reps = "", done = false)
        }.ifEmpty { listOf(SetData("—", "", "", false)) },
    )

@Composable
fun GymApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { HealthConnectManager(context) }
    val miBand = remember { MiBandHeartRateMonitor(context) }

    var screen by remember { mutableStateOf(if (UserStore.loggedIn) Screen.HOME else Screen.ONBOARDING) }
    var backStackScreen by remember { mutableStateOf(Screen.HOME) }

    // Active session state — timer lives in WorkoutTimer (shared with the service)
    val exercises = remember { mutableStateListOf<WorkoutExercise>() }
    val timerState by WorkoutTimer.state.collectAsState()
    val finishRequested by WorkoutTimer.finishRequested.collectAsState()
    val inSession = timerState.active
    var sessionTitle by remember { mutableStateOf("Workout") }
    var finishedElapsed by remember { mutableIntStateOf(0) }
    // Rest countdown, anchored to a monotonic deadline; `rest` is the displayed
    // whole-second remainder, derived by the effect below. `restTotalMs` is the
    // full span of the current rest (grows on +15s) for the pill's drain ring.
    var restDeadlineMs by remember { mutableStateOf<Long?>(null) }
    var restTotalMs by remember { mutableStateOf<Long?>(null) }
    var rest by remember { mutableStateOf<Int?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var sessionStartMillis by remember { mutableLongStateOf(0L) }
    var liveHeartRate by remember { mutableStateOf<Int?>(null) }
    var finishedAvgHr by remember { mutableStateOf<Int?>(null) }
    // Steps/calories burned so far this session, streamed live from the watch.
    var liveWorkoutSteps by remember { mutableStateOf<Long?>(null) }
    var liveWorkoutCalories by remember { mutableStateOf<Double?>(null) }
    // (elapsedSec, bpm) samples taken across the session, for the post-workout HR graph.
    val hrSamples = remember { mutableStateListOf<Pair<Int, Int>>() }
    // Which exercise the watch remote is targeting, if the user picked one there — see watchCurrentTarget()
    var watchSelectedExerciseIndex by remember { mutableStateOf<Int?>(null) }

    // Selections for detail screens
    var selectedTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var selectedWorkout by remember { mutableStateOf<SavedWorkout?>(null) }
    var selectedRoutine by remember { mutableStateOf<Routine?>(null) }
    var summaryData by remember { mutableStateOf<WorkoutSummaryData?>(null) }

    // Health Connect / dashboard
    var hcGranted by remember { mutableStateOf(false) }
    var todayStats by remember { mutableStateOf(TodayStats("—", "—", "—")) }
    var isRefreshing by remember { mutableStateOf(false) }

    // lastBackPressMs tracked here so it survives recompositions
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    suspend fun loadDashboard() {
        hcGranted = manager.hasAnyPermission()
        val steps = manager.readTodaySteps()
        val cals = manager.readTodayActiveCalories()
        val latestHr = manager.readLatestHeartRate()?.toInt()
        todayStats = TodayStats(
            steps = steps?.let { String.format(Locale.US, "%,d", it) } ?: "—",
            calories = cals?.let { it.toInt().toString() } ?: "—",
            heartRate = latestHr?.toString() ?: "—",
        )
        PhoneWearSync.pushTodayStats(context, todayStats)   // mirror to a paired watch, if any
    }

    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        scope.launch { hcGranted = manager.hasAnyPermission() }
    }

    // BLE permissions (API 31+) for connecting to a broadcasting HR monitor.
    val blePermissions = remember {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }
    val bleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) miBand.start()
    }
    // Start the Mi Band / BLE HR monitor, requesting permission first if needed.
    fun startHeartRate() {
        val granted = blePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) miBand.start() else bleLauncher.launch(blePermissions)
    }

    // Rest countdown. Deadline-anchored: +15s taps move the deadline instead of
    // restarting a 1s sleep, so the remaining time never stretches or drifts.
    fun startRest(seconds: Int) {
        restDeadlineMs = SystemClock.elapsedRealtime() + seconds * 1000L
        restTotalMs = seconds * 1000L
    }
    fun extendRest(seconds: Int) {
        val now = SystemClock.elapsedRealtime()
        restDeadlineMs = maxOf(restDeadlineMs ?: now, now) + seconds * 1000L
        restTotalMs = (restTotalMs ?: 0L) + seconds * 1000L
    }
    fun clearRest() {
        restDeadlineMs = null
        restTotalMs = null
        rest = null
    }
    LaunchedEffect(restDeadlineMs) {
        val deadline = restDeadlineMs ?: run { rest = null; return@LaunchedEffect }
        while (true) {
            val remainingMs = deadline - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) break
            rest = ((remainingMs + 999) / 1000L).toInt()
            delay(250L)
        }
        rest = null
        restDeadlineMs = null
        restTotalMs = null
    }

    // Live heart rate during a session. Two real-time broadcast sources feed it:
    // the watch's on-wrist sensor (via PhoneWearSync) and a BLE heart-rate
    // monitor such as a Mi Band in "Broadcast Heart Rate" mode (via miBand).
    // Polling Health Connect only sees whatever's already synced and can lag by
    // minutes, so it's the fallback used only when neither broadcaster is live.
    LaunchedEffect(inSession) {
        if (inSession) {
            while (true) {
                if (PhoneWearSync.watchHeartRate.value == null && miBand.heartRate.value == null) {
                    liveHeartRate = manager.readLatestHeartRate()?.toInt()
                }
                delay(15_000L)
            }
        }
    }
    LaunchedEffect(inSession) {
        if (inSession) {
            PhoneWearSync.watchHeartRate.collect { bpm -> if (bpm != null) liveHeartRate = bpm }
        }
    }
    LaunchedEffect(inSession) {
        if (inSession) {
            miBand.heartRate.collect { bpm -> if (bpm != null) liveHeartRate = bpm }
        }
    }
    // Sample heart rate over time during the session, for the post-workout graph.
    // bpm <= 0 readings are sensor warm-up noise, not real measurements.
    LaunchedEffect(inSession, liveHeartRate) {
        val bpm = liveHeartRate
        if (inSession && bpm != null && bpm > 0) hrSamples.add(WorkoutTimer.elapsed to bpm)
    }

    // Steps + active calories burned this session, streamed live from the watch.
    LaunchedEffect(inSession) {
        if (inSession) {
            PhoneWearSync.sessionSteps.collect { steps -> if (steps != null) liveWorkoutSteps = steps }
        }
    }
    LaunchedEffect(inSession) {
        if (inSession) {
            PhoneWearSync.sessionCalories.collect { cals -> if (cals != null) liveWorkoutCalories = cals }
        }
    }

    // Refresh the Health Connect dashboard when landing on Home/Profile, then
    // keep re-polling while the user stays there — without this the stats
    // (kcal especially) freeze at whatever was synced on screen entry.
    LaunchedEffect(screen, UserStore.loggedIn) {
        if (UserStore.loggedIn && (screen == Screen.HOME || screen == Screen.PROFILE)) {
            while (true) {
                loadDashboard()
                delay(60_000L)
            }
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────────
    fun refreshHome() {
        scope.launch {
            isRefreshing = true
            WorkoutRepository.reload(context)     // re-read saved workouts from disk
            RoutineRepository.reload(context)
            loadDashboard()                       // re-read Health Connect today stats
            isRefreshing = false
            Toast.makeText(context, "Refreshed!", Toast.LENGTH_SHORT).show()
        }
    }
    fun beginSession(title: String, seed: List<WorkoutExercise>, openSearch: Boolean) {
        exercises.clear(); exercises.addAll(seed)
        clearRest()
        sessionTitle = title
        sessionStartMillis = System.currentTimeMillis()
        liveHeartRate = null
        finishedAvgHr = null
        liveWorkoutSteps = null
        liveWorkoutCalories = null
        hrSamples.clear()
        watchSelectedExerciseIndex = null
        PhoneWearSync.clearWatchHeartRate()
        PhoneWearSync.clearSessionActivity()
        startHeartRate()                  // connect to a broadcasting BLE HR monitor, if any
        Haptics.workoutStart(context)     // firm buzz on start
        WorkoutTimer.start()
        WorkoutService.start(context)
        screen = Screen.ACTIVE_WORKOUT
        showSearch = openSearch
    }
    fun startEmpty() = beginSession("Workout", emptyList(), openSearch = true)
    fun startTemplate(t: WorkoutTemplate) =
        beginSession("${t.name} Workout", t.exercises.map(::newSessionExercise), openSearch = false)
    fun repeatWorkout(w: SavedWorkout) =
        beginSession(w.title, w.exercises.map(::repeatSessionExercise), openSearch = false)

    fun markSetDone(ei: Int, si: Int) {
        val ex = exercises[ei]
        val sets = ex.sets.toMutableList()
        val nowDone = !sets[si].done
        sets[si] = sets[si].copy(done = nowDone)
        exercises[ei] = ex.copy(sets = sets)
        if (nowDone) {
            startRest(90)
            Haptics.repComplete(context)  // soft tap on set complete
        }
    }
    fun addSet(ei: Int) {
        val ex = exercises[ei]
        val lastPrev = ex.sets.lastOrNull()?.prev ?: "—"
        exercises[ei] = ex.copy(sets = ex.sets + SetData(lastPrev, "", "", false))
    }
    /**
     * Exercise/set the watch remote acts on: the one the user picked from the
     * watch ([watchSelectedExerciseIndex]), or — absent a pick, or once that
     * exercise's sets are all done — the first not-yet-done set overall.
     */
    fun watchCurrentTarget(): Pair<Int, Int>? {
        val picked = watchSelectedExerciseIndex?.takeIf { it in exercises.indices }
        if (picked != null) {
            val sets = exercises[picked].sets
            if (sets.isNotEmpty()) {
                val si = sets.indexOfFirst { !it.done }.takeIf { it >= 0 } ?: sets.lastIndex
                return picked to si
            }
        }
        return firstUndoneSet(exercises)
    }
    /** Marks the watch remote's current target set done. */
    fun markCurrentSetDone() {
        watchCurrentTarget()?.let { (ei, si) -> markSetDone(ei, si) }
    }
    fun adjustCurrentWeight(deltaKg: Double) {
        val (ei, si) = watchCurrentTarget() ?: return
        val ex = exercises[ei]
        val sets = ex.sets.toMutableList()
        val updated = ((sets[si].weight.toDoubleOrNull() ?: 0.0) + deltaKg).coerceAtLeast(0.0)
        sets[si] = sets[si].copy(weight = formatWeightKg(updated))
        exercises[ei] = ex.copy(sets = sets)
    }
    fun adjustCurrentReps(delta: Int) {
        val (ei, si) = watchCurrentTarget() ?: return
        val ex = exercises[ei]
        val sets = ex.sets.toMutableList()
        val updated = ((sets[si].reps.toIntOrNull() ?: 0) + delta).coerceAtLeast(0)
        sets[si] = sets[si].copy(reps = updated.toString())
        exercises[ei] = ex.copy(sets = sets)
    }

    fun finishWorkout() {
        Haptics.workoutComplete(context)  // two short pulses on finish
        finishedElapsed = WorkoutTimer.elapsed
        summaryData = buildSummary(
            sessionTitle, finishedElapsed, exercises.toList(),
            steps = liveWorkoutSteps, calories = liveWorkoutCalories, hrSamples = hrSamples.toList(),
        )
        summaryData?.let { data ->
            // Glanceable recap for the watch — sent before clearActiveWorkout()
            // below drops it back to idle, so it has something to show first.
            val volumeKg = data.vol.replace(",", "").toIntOrNull() ?: 0
            PhoneWearSync.pushWorkoutSummary(
                context,
                WearSync.WorkoutSummary(durationSec = finishedElapsed, totalSets = data.sets, totalVolumeKg = volumeKg),
            )
        }
        // Pull the heart rate recorded across the workout window from Health Connect.
        // If nothing's synced there, fall back to what the BLE monitor averaged
        // live this session (a broadcasting Mi Band may not write to HC at all).
        val startMs = sessionStartMillis
        val fallback = miBand.sessionAverage() ?: liveHeartRate
        scope.launch {
            val hrs = manager.readHeartRateBetween(Instant.ofEpochMilli(startMs), Instant.now())
            finishedAvgHr = if (hrs.isNotEmpty()) hrs.average().toInt() else fallback
        }
        // Going inactive makes the service tear down its own notification & stop.
        WorkoutTimer.stop()
        clearRest()
        PhoneWearSync.clearActiveWorkout(context)   // watch goes back to its idle screen
        screen = Screen.SUMMARY
    }
    fun endSession() {
        WorkoutTimer.stop()               // service observes this and removes the notification
        SessionJournal.clear(context)     // session over — drop the crash-recovery journal
        exercises.clear(); clearRest(); summaryData = null; finishedElapsed = 0
        liveHeartRate = null; finishedAvgHr = null; watchSelectedExerciseIndex = null
        liveWorkoutSteps = null; liveWorkoutCalories = null; hrSamples.clear()
        miBand.stop()
        PhoneWearSync.clearWatchHeartRate()
        PhoneWearSync.clearSessionActivity()
        PhoneWearSync.clearActiveWorkout(context)
        PhoneWearSync.clearWorkoutSummary(context)
    }
    fun saveWorkout() {
        val now = System.currentTimeMillis()
        WorkoutRepository.add(
            context,
            SavedWorkout(
                id = now,
                title = sessionTitle,
                dateMillis = now,
                durationSec = finishedElapsed,
                totalVolumeKg = sessionVolume(exercises),
                totalSets = sessionSetCount(exercises),
                exercises = exercises.toList(),
                avgHeartRate = finishedAvgHr,
            ),
        )
        endSession(); screen = Screen.HOME
    }

    // Back navigation — defined after all action funs so lambdas can reference them
    BackHandler(enabled = screen != Screen.ONBOARDING) {
        when (screen) {
            // Root: double-tap to exit
            Screen.HOME -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000L) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
            // Bottom-nav tabs → back to Home
            Screen.WORKOUT_LANDING, Screen.HISTORY, Screen.PROGRESS -> screen = Screen.HOME
            // Secondary screens → return to their origin
            Screen.CREATE_ROUTINE, Screen.EDIT_ROUTINE -> screen = Screen.WORKOUT_LANDING
            Screen.TEMPLATE_DETAIL -> screen = backStackScreen
            Screen.ACTIVE_WORKOUT  -> screen = Screen.WORKOUT_LANDING
            Screen.SUMMARY         -> { endSession(); screen = Screen.HOME }
            Screen.WORKOUT_DETAIL  -> screen = backStackScreen
            Screen.PROFILE         -> screen = backStackScreen
            Screen.HEART_RATE      -> screen = backStackScreen
            Screen.STEPS           -> screen = backStackScreen
            else -> {}
        }
    }

    // Restore a session the OS killed mid-workout. The journal only exists
    // between beginSession() and endSession(), so hitting this path means the
    // process died with a workout open.
    LaunchedEffect(Unit) {
        if (WorkoutTimer.state.value.active || !UserStore.loggedIn) return@LaunchedEffect
        val restored = withContext(Dispatchers.IO) { SessionJournal.restore(context) } ?: return@LaunchedEffect
        if (WorkoutTimer.state.value.active) return@LaunchedEffect   // a session started while we read
        exercises.clear(); exercises.addAll(restored.exercises)
        sessionTitle = restored.title
        sessionStartMillis = restored.startMillis
        hrSamples.clear()
        WorkoutTimer.restore(restored.elapsedSec, restored.running)
        WorkoutService.start(context)
        startHeartRate()
        screen = Screen.ACTIVE_WORKOUT
        Toast.makeText(context, "Workout restored", Toast.LENGTH_SHORT).show()
    }

    // Journal the live session (debounced) so the restore above has data.
    // Keyed off content/title/running — not the 1 Hz elapsed tick; restore()
    // credits the running wall time from the save timestamp instead.
    LaunchedEffect(Unit) {
        snapshotFlow {
            if (timerState.active) Triple(exercises.toList(), sessionTitle, timerState.running) else null
        }
            .distinctUntilChanged()
            .collectLatest { snap ->
                if (snap == null) return@collectLatest
                delay(750L)   // let typing bursts settle
                SessionJournal.save(
                    context,
                    title = snap.second,
                    startMillis = sessionStartMillis,
                    elapsedSec = WorkoutTimer.elapsed,
                    running = snap.third,
                    exercises = snap.first,
                )
            }
    }

    // Finish requested from the notification action
    LaunchedEffect(finishRequested) {
        if (finishRequested && timerState.active) finishWorkout()
    }

    // Mirror the saved-workout list to the watch's history browser whenever it
    // changes — added/deleted workouts, or a different account's data loaded.
    val savedWorkouts = WorkoutRepository.workouts
    LaunchedEffect(savedWorkouts.size, savedWorkouts.firstOrNull()?.id) {
        PhoneWearSync.pushWorkoutHistory(context, savedWorkouts.toList())
    }

    // "Start workout" requested from the watch companion
    val wearStartRequested by PhoneWearSync.startWorkoutRequested.collectAsState()
    LaunchedEffect(wearStartRequested) {
        if (wearStartRequested) {
            if (!inSession) startEmpty()
            PhoneWearSync.consumeStartWorkoutRequest()
        }
    }

    // Mirror the in-progress session to the watch on every *structural* change,
    // including which exercise/set the remote currently targets (see
    // watchCurrentTarget) and that set's editable values. Ticking values are
    // deliberately absent from the effect key — elapsed/rest get stamped in at
    // push time together with a wall-clock anchor, and the watch advances both
    // locally from it, so nothing is written to the Data Layer once a second.
    val activeSnapshot = if (inSession) {
        val target = watchCurrentTarget()
        val targetEx = target?.let { exercises.getOrNull(it.first) }
        val targetSet = target?.let { (ei, si) -> exercises.getOrNull(ei)?.sets?.getOrNull(si) }
        val (exerciseName, setProgress) = if (target != null && targetEx != null) {
            targetEx.name to "Set ${target.second + 1} of ${targetEx.sets.size}"
        } else activeWorkoutLabel(exercises)
        WearSync.ActiveWorkoutSnapshot(
            running = timerState.running,
            elapsedSec = 0,    // stamped at push time
            exerciseName = exerciseName,
            setProgress = setProgress,
            restSec = null,    // stamped at push time
            exercises = exercises.map { it.name },
            currentExerciseIndex = target?.first ?: 0,
            currentWeight = targetSet?.weight.orEmpty(),
            currentReps = targetSet?.reps.orEmpty(),
            currentSetType = targetSet?.type ?: SetType.NORMAL,
        )
    } else null
    LaunchedEffect(activeSnapshot, restDeadlineMs) {
        activeSnapshot?.let { snapshot ->
            val restRemaining = restDeadlineMs?.let { d ->
                (((d - SystemClock.elapsedRealtime()) + 999) / 1000L).toInt().takeIf { it > 0 }
            }
            PhoneWearSync.pushActiveWorkout(
                context,
                snapshot.copy(
                    elapsedSec = WorkoutTimer.elapsed,
                    restSec = restRemaining,
                    anchorMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    // Mirror exercise/set progress to WorkoutProgress so the Live Update
    // notification (WorkoutService) can render a real progress bar + status chip.
    val progressExercises = if (inSession) {
        exercises.map { WorkoutProgress.ExerciseProgress(it.name, it.sets.size, it.sets.count { s -> s.done }) }
    } else null
    val progressIndex = if (inSession) watchCurrentTarget()?.first ?: 0 else 0
    LaunchedEffect(progressExercises, progressIndex) {
        if (progressExercises != null) WorkoutProgress.update(progressExercises, progressIndex)
        else WorkoutProgress.clear()
    }

    // Remote-control taps from the watch while a session is running.
    // Reads WorkoutTimer.state.value directly (not the recomposition-scoped
    // `inSession`) since this collector is launched once and must always see
    // the live session state, not whatever it was when first composed.
    LaunchedEffect(Unit) {
        PhoneWearSync.activeWorkoutCommands.collect { command ->
            if (!WorkoutTimer.state.value.active) return@collect
            when (command) {
                ActiveWorkoutCommand.TOGGLE_PAUSE -> WorkoutTimer.toggle()
                ActiveWorkoutCommand.MARK_SET_DONE -> markCurrentSetDone()
                ActiveWorkoutCommand.FINISH_WORKOUT -> finishWorkout()
                ActiveWorkoutCommand.ADD_SET -> watchCurrentTarget()?.let { (ei, _) -> addSet(ei) }
                ActiveWorkoutCommand.ADD_REST -> extendRest(15)
                ActiveWorkoutCommand.SKIP_REST -> clearRest()
            }
        }
    }
    LaunchedEffect(Unit) {
        PhoneWearSync.saveWorkoutRequested.collect { if (screen == Screen.SUMMARY) saveWorkout() }
    }
    LaunchedEffect(Unit) {
        PhoneWearSync.discardWorkoutRequested.collect {
            if (screen == Screen.SUMMARY) { endSession(); screen = Screen.HOME }
        }
    }
    LaunchedEffect(Unit) {
        PhoneWearSync.selectExerciseRequests.collect { index ->
            if (WorkoutTimer.state.value.active && index in exercises.indices) {
                watchSelectedExerciseIndex = index
            }
        }
    }
    LaunchedEffect(Unit) {
        PhoneWearSync.weightAdjustments.collect { delta ->
            if (WorkoutTimer.state.value.active) adjustCurrentWeight(delta)
        }
    }
    LaunchedEffect(Unit) {
        PhoneWearSync.repAdjustments.collect { delta ->
            if (WorkoutTimer.state.value.active) adjustCurrentReps(delta)
        }
    }

    val navActive = when (screen) {
        Screen.HOME -> NavItem.HOME
        Screen.WORKOUT_LANDING, Screen.ACTIVE_WORKOUT -> NavItem.WORKOUT
        Screen.HISTORY -> NavItem.HISTORY
        Screen.PROGRESS -> NavItem.PROGRESS
        Screen.PROFILE -> NavItem.PROFILE
        else -> NavItem.HOME
    }
    val showNav = screen in setOf(
        Screen.HOME, Screen.WORKOUT_LANDING, Screen.ACTIVE_WORKOUT, Screen.HISTORY, Screen.PROGRESS, Screen.PROFILE,
    )

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { screenTransition() },
                    label = "screen",
                ) { current ->
                when (current) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        manager = manager,
                        onComplete = { name, email, gender, heightCm, weightKg, birthdayMillis ->
                            UserStore.createAccount(name, email, gender)
                            if (heightCm != null || weightKg != null || birthdayMillis != null) {
                                UserStore.updateProfile(name, email, heightCm, weightKg, birthdayMillis, gender)
                            }
                            scope.launch {
                                WorkoutRepository.reload(context)
                                RoutineRepository.reload(context)
                            }
                            screen = Screen.HOME
                        },
                        onLogin = {
                            UserStore.login()
                            scope.launch {
                                WorkoutRepository.reload(context)    // restore this account's data
                                RoutineRepository.reload(context)
                            }
                            screen = Screen.HOME
                        },
                    )

                    Screen.HOME -> HomeScreen(
                        today = todayStats,
                        hcConnected = hcGranted,
                        isRefreshing = isRefreshing,
                        onRefresh = { refreshHome() },
                        onStartEmpty = { startEmpty() },
                        onTemplate = { t -> selectedTemplate = t; backStackScreen = Screen.HOME; screen = Screen.TEMPLATE_DETAIL },
                        onOpenWorkout = { w -> selectedWorkout = w; backStackScreen = Screen.HOME; screen = Screen.WORKOUT_DETAIL },
                        onProfile = { backStackScreen = Screen.HOME; screen = Screen.PROFILE },
                        onSeeAllHistory = { screen = Screen.HISTORY },
                        onHeartRate = { backStackScreen = Screen.HOME; screen = Screen.HEART_RATE },
                        onSteps = { backStackScreen = Screen.HOME; screen = Screen.STEPS },
                    )

                    Screen.WORKOUT_LANDING -> WorkoutLandingScreen(
                        onStartEmpty = { startEmpty() },
                        onTemplate = { t -> selectedTemplate = t; backStackScreen = Screen.WORKOUT_LANDING; screen = Screen.TEMPLATE_DETAIL },
                        onRepeat = { w -> repeatWorkout(w) },
                        onCreateRoutine = { screen = Screen.CREATE_ROUTINE },
                        onEditRoutine = { r -> selectedRoutine = r; screen = Screen.EDIT_ROUTINE },
                    )

                    Screen.CREATE_ROUTINE -> CreateRoutineScreen(
                        onBack = { screen = Screen.WORKOUT_LANDING },
                        onSave = { routine ->
                            RoutineRepository.add(context, routine)
                            screen = Screen.WORKOUT_LANDING
                        },
                    )

                    Screen.EDIT_ROUTINE -> selectedRoutine?.let { r ->
                        CreateRoutineScreen(
                            existing = r,
                            onBack = { screen = Screen.WORKOUT_LANDING },
                            onSave = { updated ->
                                RoutineRepository.update(context, updated)
                                screen = Screen.WORKOUT_LANDING
                            },
                        )
                    }

                    Screen.TEMPLATE_DETAIL -> selectedTemplate?.let { t ->
                        val routineId = t.id.removePrefix("routine-").toLongOrNull()
                            ?.takeIf { t.id.startsWith("routine-") }
                        TemplateDetailScreen(
                            template = t,
                            onBack = { screen = backStackScreen },
                            onStart = { startTemplate(it) },
                            onDelete = {
                                if (routineId != null) {
                                    RoutineRepository.delete(context, routineId)
                                } else {
                                    UserStore.deleteTemplate(t.id)
                                }
                                screen = backStackScreen
                            },
                        )
                    }

                    Screen.ACTIVE_WORKOUT -> ActiveWorkoutScreen(
                        exercises = exercises,
                        running = timerState.running,
                        heartRate = liveHeartRate,
                        steps = liveWorkoutSteps,
                        calories = liveWorkoutCalories,
                        restDeadlineMs = restDeadlineMs,
                        restTotalMs = restTotalMs,
                        onFinish = { finishWorkout() },
                        onTogglePause = { WorkoutTimer.toggle() },
                        onOpenSearch = { showSearch = true },
                        onToggleOpen = { ei -> exercises[ei] = exercises[ei].copy(open = !exercises[ei].open) },
                        onField = { ei, si, key, value ->
                            val ex = exercises[ei]
                            val sets = ex.sets.toMutableList()
                            sets[si] = when (key) {
                                "weight" -> sets[si].copy(weight = value)
                                "reps" -> sets[si].copy(reps = value)
                                else -> sets[si]
                            }
                            exercises[ei] = ex.copy(sets = sets)
                        },
                        onToggleSet = { ei, si -> markSetDone(ei, si) },
                        onSetType = { ei, si, type ->
                            val ex = exercises[ei]
                            val sets = ex.sets.toMutableList()
                            sets[si] = sets[si].copy(type = type)
                            exercises[ei] = ex.copy(sets = sets)
                        },
                        onAddSet = { ei -> addSet(ei) },
                        onRemoveSet = { ei, si ->
                            val ex = exercises[ei]
                            if (ex.sets.size > 1) exercises[ei] = ex.copy(sets = ex.sets.toMutableList().also { it.removeAt(si) })
                        },
                        onAddRest = { extendRest(15) },
                        onSkipRest = { clearRest() },
                    )

                    Screen.SUMMARY -> summaryData?.let { data ->
                        SummaryScreen(
                            summary = data,
                            onSave = { saveWorkout() },
                            onDiscard = { endSession(); screen = Screen.HOME },
                        )
                    }

                    Screen.HISTORY -> HistoryScreen(
                        onOpenWorkout = { w -> selectedWorkout = w; backStackScreen = Screen.HISTORY; screen = Screen.WORKOUT_DETAIL },
                    )

                    Screen.WORKOUT_DETAIL -> selectedWorkout?.let { w ->
                        WorkoutDetailScreen(
                            workout = w,
                            onBack = { screen = backStackScreen },
                            onDelete = { WorkoutRepository.delete(context, it.id); screen = backStackScreen },
                        )
                    }

                    Screen.PROGRESS -> ProgressScreen()

                    Screen.HEART_RATE -> HeartRateScreen(
                        manager = manager,
                        onBack = { screen = backStackScreen },
                    )

                    Screen.STEPS -> StepsScreen(
                        manager = manager,
                        onBack = { screen = backStackScreen },
                    )

                    Screen.PROFILE -> ProfileScreen(
                        onBack = { screen = backStackScreen },
                        hcAvailable = manager.isAvailable,
                        hcGranted = hcGranted,
                        onManageHealth = { if (manager.isAvailable) hcLauncher.launch(manager.permissions) },
                        onLogout = {
                            endSession()
                            UserStore.logout()
                            screen = Screen.ONBOARDING
                        },
                        onDeleteAccount = {
                            endSession()
                            val ctx = context
                            WorkoutRepository.deleteAll(ctx)
                            RoutineRepository.deleteAll(ctx)
                            UserStore.deleteAccount()
                            screen = Screen.ONBOARDING
                        },
                    )
                }
                }
            }

            if (showNav) {
                BottomNav(active = navActive, onNav = { item ->
                    when (item) {
                        NavItem.HOME -> screen = Screen.HOME
                        NavItem.WORKOUT -> screen = if (inSession) Screen.ACTIVE_WORKOUT else Screen.WORKOUT_LANDING
                        NavItem.HISTORY -> screen = Screen.HISTORY
                        NavItem.PROGRESS -> screen = Screen.PROGRESS
                        NavItem.PROFILE -> { backStackScreen = Screen.HOME; screen = Screen.PROFILE }
                    }
                })
            }
        }

        // Exercise search bottom sheet (active workout)
        if (showSearch) {
            ExerciseSearchSheet(
                onClose = { showSearch = false },
                onAdd = { lib -> exercises.add(newSessionExercise(lib)) },
                onRemove = { lib -> exercises.removeAll { it.name == lib.name } },
                currentExerciseNames = exercises.map { it.name }.toSet(),
            )
        }
    }
}
