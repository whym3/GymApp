# GymLog

A workout tracking app for Android built with Jetpack Compose and Health Connect.

## Features

- **Workout logging** — Start an empty session or pick a built-in split template. Live rest timer with pause/resume. Rep counts via dropdown (5–20), weight as free text. Haptic feedback on start, set completion, and finish.
- **Workout templates & custom routines** — Six built-in splits (Push, Pull, Legs, etc.) plus a routine builder where you create and name your own plans from the full exercise library.
- **Exercise library** — 33 exercises grouped by muscle group (Chest, Back, Shoulders, Arms, Legs, Core, Cardio).
- **History** — Browse every saved workout. Tap any session to see full exercise/set breakdown, duration, volume, and average heart rate. Delete sessions you don't want.
- **Progress** — Strength chart tracks your best set per exercise over time. Body tab shows bodyweight history and a BMI tracker, both built from profile entries (no Health Connect required for body data).
- **Health Connect integration** — Steps (today + 7-day history), active calories, and heart rate (live card + 7-day detail graph) pulled from Health Connect. Degrades gracefully when HC is unavailable or permissions are not granted.
- **Live heart rate during workouts** — Polls Health Connect every 15 s while a session is active; stored as an average when the session is saved.
- **Foreground workout service** — Persistent notification shows elapsed time with Pause/Resume and Finish actions so the workout survives backgrounding.
- **Streak tracking** — Weekly-leniency streak: any week with ≥ 4 workout days keeps the streak alive.
- **Per-account data isolation** — Each account gets its own workout and routine files (UUID-keyed). Log out and back in without losing data; switch accounts for a clean slate.
- **Profile** — Name, email, height, weight, birthday. Bodyweight history feeds the Progress chart. BMI is computed and colour-coded. Profile photo via the system photo picker.
- **Theming** — Light, dark, and system-default modes. Electric-blue accent (`#4F6EF7`) throughout.
- **Adaptive icon** — Bold white dumbbell on a blue gradient, works with all launcher mask shapes.

## Tech stack

| Layer | Library / API |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | `Screen` enum state machine (no Navigation-Compose) |
| Persistence | `org.json` → `filesDir` (no Room/KSP) |
| Health data | Health Connect SDK `1.1.0-rc02` |
| Background | Foreground service (`WorkoutService`) + `StateFlow` timer |
| Build | AGP 9.2.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01 |

## Requirements

- Android 12+ (API 31) — required for adaptive icons and `VibrationEffect` amplitude control
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed for steps/calories/heart rate (app works without it)

## Building

1. Clone the repo and open in Android Studio Ladybug or later.
2. Let Gradle sync finish.
3. Run on a device or emulator (API 31+).

> The Gradle daemon requires local socket connections that are blocked in some sandboxed environments. Always build from Android Studio or your own shell.

## Permissions

| Permission | Purpose |
|---|---|
| `VIBRATE` | Haptic feedback |
| `POST_NOTIFICATIONS` | Workout timer notification |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Workout foreground service |
| `health.READ_STEPS` | Daily step count |
| `health.READ_ACTIVE_CALORIES_BURNED` | Active calories |
| `health.READ_HEART_RATE` | Live + historical heart rate |
| `health.READ_WEIGHT` | (declared; body data now sourced from profile) |

## Project structure

```
app/src/main/java/com/example/gymapp/
├── MainActivity.kt          # Single-activity host, all navigation state
├── Data.kt                  # Exercise library, templates, data classes
├── WorkoutRepository.kt     # Saved workouts (per-account JSON)
├── RoutineRepository.kt     # User routines (per-account JSON)
├── UserStore.kt             # Account, profile, weight history
├── ThemeStore.kt            # Light/dark/system preference
├── HealthConnectManager.kt  # HC reads (steps, calories, HR, weight)
├── WorkoutTimer.kt          # StateFlow timer singleton
├── WorkoutService.kt        # Foreground service + notification
├── Haptics.kt               # Vibration helpers
├── Components.kt            # Shared composables (BottomNav, ProfileAvatar, …)
├── HomeScreen.kt
├── OnboardingScreen.kt
├── WorkoutLandingScreen.kt
├── CreateRoutineScreen.kt
├── TemplateDetailScreen.kt
├── ActiveWorkoutScreen.kt
├── ExerciseSearchSheet.kt
├── SummaryScreen.kt
├── HistoryScreen.kt
├── WorkoutDetailScreen.kt
├── ProgressScreen.kt
├── ProfileScreen.kt
├── HeartRateScreen.kt
└── StepsScreen.kt
```
