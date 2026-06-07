package com.example.gymapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.gymapp.ui.theme.BgColor
import com.example.gymapp.ui.theme.GymAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

enum class Screen {
    ONBOARDING, HOME, WORKOUT_LANDING, CREATE_ROUTINE, TEMPLATE_DETAIL,
    ACTIVE_WORKOUT, SUMMARY, HISTORY, WORKOUT_DETAIL, PROGRESS, PROFILE, HEART_RATE, STEPS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserStore.init(applicationContext)
        ThemeStore.init(applicationContext)
        PhoneWearSync.init(applicationContext)
        WorkoutRepository.init(applicationContext)
        RoutineRepository.init(applicationContext)
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

    var screen by remember { mutableStateOf(if (UserStore.loggedIn) Screen.HOME else Screen.ONBOARDING) }
    var backStackScreen by remember { mutableStateOf(Screen.HOME) }

    // Active session state — timer lives in WorkoutTimer (shared with the service)
    val exercises = remember { mutableStateListOf<WorkoutExercise>() }
    val timerState by WorkoutTimer.state.collectAsState()
    val finishRequested by WorkoutTimer.finishRequested.collectAsState()
    val inSession = timerState.active
    var sessionTitle by remember { mutableStateOf("Workout") }
    var finishedElapsed by remember { mutableIntStateOf(0) }
    var rest by remember { mutableStateOf<Int?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var sessionStartMillis by remember { mutableLongStateOf(0L) }
    var liveHeartRate by remember { mutableStateOf<Int?>(null) }
    var finishedAvgHr by remember { mutableStateOf<Int?>(null) }

    // Selections for detail screens
    var selectedTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var selectedWorkout by remember { mutableStateOf<SavedWorkout?>(null) }
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
        val hr = manager.readTodayHeartRate().lastOrNull()?.second
        todayStats = TodayStats(
            steps = steps?.let { String.format(Locale.US, "%,d", it) } ?: "—",
            calories = cals?.let { it.toInt().toString() } ?: "—",
            heartRate = hr?.toString() ?: "—",
        )
        PhoneWearSync.pushTodayStats(context, todayStats)   // mirror to a paired watch, if any
    }

    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        scope.launch { hcGranted = manager.hasAnyPermission() }
    }

    // Rest countdown
    val restNow = rest
    LaunchedEffect(restNow) {
        if (restNow != null && restNow > 0) {
            delay(1000L); rest = restNow - 1
        } else if (restNow == 0) {
            rest = null
        }
    }

    // Live heart rate during a session (polled from Health Connect)
    LaunchedEffect(inSession) {
        if (inSession) {
            while (true) {
                liveHeartRate = manager.readLatestHeartRate()?.toInt()
                delay(15_000L)
            }
        }
    }

    // Refresh Health Connect dashboard whenever we land on Home/Profile
    LaunchedEffect(screen, UserStore.loggedIn) {
        if (UserStore.loggedIn && (screen == Screen.HOME || screen == Screen.PROFILE)) {
            loadDashboard()
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
        rest = null
        sessionTitle = title
        sessionStartMillis = System.currentTimeMillis()
        liveHeartRate = null
        finishedAvgHr = null
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
            rest = 90
            Haptics.repComplete(context)  // soft tap on set complete
        }
    }
    /** Marks the first not-yet-done set — what the watch remote's "Done" tap targets. */
    fun markCurrentSetDone() {
        firstUndoneSet(exercises)?.let { (ei, si) -> markSetDone(ei, si) }
    }

    fun finishWorkout() {
        Haptics.workoutComplete(context)  // two short pulses on finish
        finishedElapsed = WorkoutTimer.elapsed
        summaryData = buildSummary(sessionTitle, finishedElapsed, exercises.toList())
        // Pull the heart rate recorded across the workout window from Health Connect.
        val startMs = sessionStartMillis
        val fallback = liveHeartRate
        scope.launch {
            val hrs = manager.readHeartRateBetween(Instant.ofEpochMilli(startMs), Instant.now())
            finishedAvgHr = if (hrs.isNotEmpty()) hrs.average().toInt() else fallback
        }
        // Going inactive makes the service tear down its own notification & stop.
        WorkoutTimer.stop()
        rest = null
        PhoneWearSync.clearActiveWorkout(context)   // watch goes back to its idle screen
        screen = Screen.SUMMARY
    }
    fun endSession() {
        WorkoutTimer.stop()               // service observes this and removes the notification
        exercises.clear(); rest = null; summaryData = null; finishedElapsed = 0
        liveHeartRate = null; finishedAvgHr = null
        PhoneWearSync.clearActiveWorkout(context)
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
            Screen.CREATE_ROUTINE  -> screen = Screen.WORKOUT_LANDING
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

    // Finish requested from the notification action
    LaunchedEffect(finishRequested) {
        if (finishRequested && timerState.active) finishWorkout()
    }

    // "Start workout" requested from the watch companion
    val wearStartRequested by PhoneWearSync.startWorkoutRequested.collectAsState()
    LaunchedEffect(wearStartRequested) {
        if (wearStartRequested) {
            if (!inSession) startEmpty()
            PhoneWearSync.consumeStartWorkoutRequest()
        }
    }

    // Mirror the in-progress session to the watch on every change
    val activeSnapshot = if (inSession) {
        val (exerciseName, setProgress) = activeWorkoutLabel(exercises)
        WearSync.ActiveWorkoutSnapshot(
            running = timerState.running,
            elapsedSec = timerState.elapsedSec,
            exerciseName = exerciseName,
            setProgress = setProgress,
            restSec = rest,
        )
    } else null
    LaunchedEffect(activeSnapshot) {
        activeSnapshot?.let { PhoneWearSync.pushActiveWorkout(context, it) }
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
            }
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
                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        manager = manager,
                        onComplete = { name, email ->
                            UserStore.createAccount(name, email)   // new accountId
                            WorkoutRepository.reload(context)       // load this account's (empty) data
                            RoutineRepository.reload(context)
                            screen = Screen.HOME
                        },
                        onLogin = {
                            UserStore.login()
                            WorkoutRepository.reload(context)        // restore this account's data
                            RoutineRepository.reload(context)
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
                    )

                    Screen.CREATE_ROUTINE -> CreateRoutineScreen(
                        onBack = { screen = Screen.WORKOUT_LANDING },
                        onSave = { routine ->
                            RoutineRepository.add(context, routine)
                            screen = Screen.WORKOUT_LANDING
                        },
                    )

                    Screen.TEMPLATE_DETAIL -> selectedTemplate?.let { t ->
                        val routineId = t.id.removePrefix("routine-").toLongOrNull()
                            ?.takeIf { t.id.startsWith("routine-") }
                        TemplateDetailScreen(
                            template = t,
                            onBack = { screen = backStackScreen },
                            onStart = { startTemplate(it) },
                            onDelete = routineId?.let { id ->
                                {
                                    RoutineRepository.delete(context, id)
                                    screen = backStackScreen
                                }
                            },
                        )
                    }

                    Screen.ACTIVE_WORKOUT -> ActiveWorkoutScreen(
                        exercises = exercises,
                        elapsed = timerState.elapsedSec,
                        running = timerState.running,
                        heartRate = liveHeartRate,
                        rest = rest,
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
                        onAddSet = { ei ->
                            val ex = exercises[ei]
                            val lastPrev = ex.sets.lastOrNull()?.prev ?: "—"
                            exercises[ei] = ex.copy(sets = ex.sets + SetData(lastPrev, "", "", false))
                        },
                        onAddRest = { rest = (rest ?: 0) + 15 },
                        onSkipRest = { rest = null },
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
            )
        }
    }
}
