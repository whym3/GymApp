package com.example.gymapp

import androidx.compose.ui.graphics.Color
import com.example.gymapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val muscleColors: Map<String, Color> = mapOf(
    "Chest"     to MuscleChest,
    "Back"      to MuscleBack,
    "Legs"      to MuscleLegs,
    "Shoulders" to MuscleShoulders,
    "Arms"      to MuscleArms,
    "Core"      to MuscleCore,
)

fun muscleColor(group: String) = muscleColors[group] ?: SubTextColor

// ── Models ──────────────────────────────────────────────────────────────────

data class ExerciseLibraryItem(val name: String, val group: String, val equip: String)

data class WorkoutTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val exercises: List<ExerciseLibraryItem>,
) {
    val groups: List<String> get() = exercises.map { it.group }.distinct()
    val count: Int get() = exercises.size
}

data class SetData(val prev: String, val weight: String, val reps: String, val done: Boolean)

data class WorkoutExercise(
    val id: String,
    val name: String,
    val group: String,
    val equip: String,
    val open: Boolean = true,
    val sets: List<SetData> = listOf(SetData("—", "", "", false)),
)

/** A completed, persisted workout. */
data class SavedWorkout(
    val id: Long,
    val title: String,
    val dateMillis: Long,
    val durationSec: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val exercises: List<WorkoutExercise>,
    val avgHeartRate: Int? = null,
) {
    val muscleGroups: List<String> get() = exercises.map { it.group }.distinct()
}

data class PrItem(val name: String, val detail: String, val delta: String)
data class LogExercise(val name: String, val group: String, val sets: List<String>)
data class WorkoutSummaryData(
    val title: String,
    val dur: String,
    val sets: Int,
    val vol: String,
    val prs: List<PrItem>,
    val log: List<LogExercise>,
)

/** Today's dashboard stats. */
data class TodayStats(
    val steps: String,
    val calories: String,
    val heartRate: String,
)

// ── Static / mock data ────────────────────────────────────────────────────────

/** Workout days per week needed to keep a streak alive. */
const val WEEKLY_GOAL_DAYS = 4

val exerciseLibrary = listOf(
    // Chest
    ExerciseLibraryItem("Bench Press",                 "Chest",     "Barbell"),
    ExerciseLibraryItem("Incline Dumbbell Press",      "Chest",     "Dumbbell"),
    ExerciseLibraryItem("Flat Smith Press (15° angle)","Chest",     "Machine"),
    ExerciseLibraryItem("TNF Press (45° angle)",       "Chest",     "Machine"),
    ExerciseLibraryItem("Pec-Dec",                     "Chest",     "Machine"),
    ExerciseLibraryItem("Cable Fly",                   "Chest",     "Machine"),
    ExerciseLibraryItem("Dumbbell Pullover",           "Chest",     "Dumbbell"),
    ExerciseLibraryItem("Pushups",                     "Chest",     "Bodyweight"),
    // Back
    ExerciseLibraryItem("Pullup",                      "Back",      "Bodyweight"),
    ExerciseLibraryItem("Barbell Row",                 "Back",      "Barbell"),
    ExerciseLibraryItem("Dumbbell Row",                "Back",      "Dumbbell"),
    ExerciseLibraryItem("Lat Pulldown",                "Back",      "Machine"),
    ExerciseLibraryItem("Seated Cable Row",            "Back",      "Machine"),
    // Legs
    ExerciseLibraryItem("Squat",                       "Legs",      "Barbell"),
    ExerciseLibraryItem("Goblet Squat",                "Legs",      "Dumbbell"),
    ExerciseLibraryItem("Romanian Deadlift",           "Legs",      "Barbell"),
    ExerciseLibraryItem("Leg Press",                   "Legs",      "Machine"),
    ExerciseLibraryItem("Leg Extension",               "Legs",      "Machine"),
    ExerciseLibraryItem("Leg Curl",                    "Legs",      "Machine"),
    ExerciseLibraryItem("Walking Lunge",               "Legs",      "Dumbbell"),
    ExerciseLibraryItem("Calf Raises",                 "Legs",      "Machine"),
    // Shoulders
    ExerciseLibraryItem("Shoulder Press",              "Shoulders", "Barbell"),
    ExerciseLibraryItem("Lateral Raises",              "Shoulders", "Dumbbell"),
    ExerciseLibraryItem("Rear Delt Fly",               "Shoulders", "Dumbbell"),
    ExerciseLibraryItem("Face Pulls",                  "Shoulders", "Machine"),
    // Arms
    ExerciseLibraryItem("Barbell Curl",                "Arms",      "Barbell"),
    ExerciseLibraryItem("Preacher Curl",               "Arms",      "Barbell"),
    ExerciseLibraryItem("Hammer Curl",                 "Arms",      "Dumbbell"),
    ExerciseLibraryItem("JM Press",                    "Arms",      "Barbell"),
    ExerciseLibraryItem("Tricep Pushdown",             "Arms",      "Machine"),
    // Core
    ExerciseLibraryItem("Hanging Leg Raise",           "Core",      "Bodyweight"),
    ExerciseLibraryItem("Cable Crunch",                "Core",      "Machine"),
    ExerciseLibraryItem("Plank",                       "Core",      "Bodyweight"),
)

private fun lib(name: String) = exerciseLibrary.first { it.name == name }

val mockTemplates = listOf(
    WorkoutTemplate(
        id = "push", name = "Push", subtitle = "Chest · Shoulders · Triceps",
        description = "A horizontal & vertical pressing day. Build a bigger chest, stronger shoulders and triceps with compound presses followed by isolation work.",
        exercises = listOf("Bench Press", "Incline Dumbbell Press", "Shoulder Press", "Lateral Raises", "Cable Fly", "Tricep Pushdown").map(::lib),
    ),
    WorkoutTemplate(
        id = "pull", name = "Pull", subtitle = "Back · Biceps",
        description = "Pulling movements for a wide, thick back and bigger arms. Start heavy with rows and pull-ups, finish with curls.",
        exercises = listOf("Pullup", "Barbell Row", "Lat Pulldown", "Seated Cable Row", "Barbell Curl", "Hammer Curl").map(::lib),
    ),
    WorkoutTemplate(
        id = "legs", name = "Legs", subtitle = "Quads · Hamstrings · Core",
        description = "The hardest day of the week. Squat and hinge patterns to grow your legs, plus core work to finish.",
        exercises = listOf("Squat", "Romanian Deadlift", "Leg Press", "Leg Extension", "Walking Lunge", "Hanging Leg Raise").map(::lib),
    ),
    WorkoutTemplate(
        id = "full", name = "Full Body", subtitle = "Everything, balanced",
        description = "Hit every major muscle group in one efficient session. Great for 2–3x per week training splits.",
        exercises = listOf("Bench Press", "Squat", "Barbell Row", "Shoulder Press", "Romanian Deadlift", "Pullup", "Barbell Curl", "Plank").map(::lib),
    ),
)

// ── Formatters ────────────────────────────────────────────────────────────────

fun formatWorkoutDate(millis: Long): String {
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, then)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday"
    return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
}

fun formatDuration(sec: Int): String {
    val m = sec / 60
    return if (m < 60) "$m min"
    else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}

fun formatVolume(kg: Double): String =
    if (kg >= 1000) String.format(Locale.US, "%.1fk kg", kg / 1000) else "${kg.toInt()} kg"

/** Compact volume without unit, e.g. "8.2k" or "820". */
fun shortVolume(kg: Double): String =
    if (kg >= 1000) String.format(Locale.US, "%.1fk", kg / 1000) else kg.toInt().toString()

fun bmiCategory(bmi: Double): String = when {
    bmi < 18.5 -> "Underweight"
    bmi < 25.0 -> "Normal"
    bmi < 30.0 -> "Overweight"
    else -> "Obese"
}

fun formatClock(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
