package com.example.gymapp

// ── Static exercise data ─────────────────────────────────────────────────────

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
