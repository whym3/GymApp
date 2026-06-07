package com.example.gymapp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * App-wide store for completed workouts. Persists to a JSON file in internal
 * storage and exposes a Compose snapshot list so screens recompose on change.
 *
 * No external persistence library is used (org.json ships with Android), which
 * keeps the build free of annotation processors / compiler plugins.
 */
object WorkoutRepository {

    private val _workouts = mutableStateListOf<SavedWorkout>()
    val workouts: List<SavedWorkout> get() = _workouts

    fun init(context: Context) {
        reload(context)
    }

    /** (Re)load the current account's workouts from disk. */
    fun reload(context: Context) {
        _workouts.clear()
        _workouts.addAll(readFromDisk(context).sortedByDescending { it.dateMillis })
    }

    fun add(context: Context, workout: SavedWorkout) {
        _workouts.add(0, workout)
        writeToDisk(context)
    }

    fun delete(context: Context, id: Long) {
        _workouts.removeAll { it.id == id }
        writeToDisk(context)
    }

    fun deleteAll(context: Context) {
        _workouts.clear()
        runCatching { file(context).delete() }
    }

    val totalWorkouts: Int get() = _workouts.size

    fun workoutsThisWeek(): Int {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return _workouts.count { it.dateMillis >= weekAgo }
    }

    fun volumeToday(): Double {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return _workouts.filter { it.dateMillis >= startOfDay }.sumOf { it.totalVolumeKg }
    }

    /**
     * Current workout streak in days. Starts at 0 for a brand-new user (so it
     * grows from their first workout onward, not pre-seeded).
     *
     * Leniency rule: a streak is NOT broken by rest days as long as the user
     * trains at least 4 days in that calendar week (Mon–Sun). Any week that hits
     * the 4-day goal has all of its days counted toward the streak, so missing a
     * day or two inside a good week keeps the run alive. A week that finishes with
     * fewer than 4 workout days breaks the streak.
     */
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        if (_workouts.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val workoutDays = _workouts
            .map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
            .toSet()
        val firstDay = workoutDays.minOrNull() ?: return 0

        // Days that count toward the streak: every workout day, plus every day of
        // any week that reached the 4-day goal (capped to [firstDay, today]).
        val credited = HashSet<LocalDate>(workoutDays)
        workoutDays.groupBy { mondayOf(it) }.forEach { (monday, daysInWeek) ->
            if (daysInWeek.size >= WEEKLY_GOAL_DAYS) {
                var d = monday
                val sunday = monday.plusDays(6)
                while (!d.isAfter(sunday)) {
                    if (!d.isBefore(firstDay) && !d.isAfter(today)) credited.add(d)
                    d = d.plusDays(1)
                }
            }
        }

        // Count back from today (or yesterday, if today isn't done yet) while the
        // days remain credited.
        var cursor = if (credited.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (credited.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** Best (heaviest) logged weight for an exercise across all saved workouts. */
    fun bestWeightFor(name: String): Double? =
        _workouts.asSequence()
            .flatMap { it.exercises.asSequence() }
            .filter { it.name == name }
            .flatMap { it.sets.asSequence() }
            .mapNotNull { it.weight.toDoubleOrNull() }
            .maxOrNull()

    // ── Disk I/O (JSON) ────────────────────────────────────────────────────────

    private fun file(context: Context): File {
        val id = UserStore.accountId.ifBlank { "default" }
        return File(context.filesDir, "workouts_$id.json")
    }

    private fun writeToDisk(context: Context) {
        val arr = JSONArray()
        _workouts.forEach { w ->
            val exArr = JSONArray()
            w.exercises.forEach { ex ->
                val setArr = JSONArray()
                ex.sets.forEach { s ->
                    setArr.put(
                        JSONObject()
                            .put("prev", s.prev)
                            .put("weight", s.weight)
                            .put("reps", s.reps)
                            .put("done", s.done)
                    )
                }
                exArr.put(
                    JSONObject()
                        .put("id", ex.id)
                        .put("name", ex.name)
                        .put("group", ex.group)
                        .put("equip", ex.equip)
                        .put("sets", setArr)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("title", w.title)
                    .put("dateMillis", w.dateMillis)
                    .put("durationSec", w.durationSec)
                    .put("totalVolumeKg", w.totalVolumeKg)
                    .put("totalSets", w.totalSets)
                    .put("avgHeartRate", w.avgHeartRate ?: -1)
                    .put("exercises", exArr)
            )
        }
        runCatching { file(context).writeText(arr.toString()) }
    }

    private fun readFromDisk(context: Context): List<SavedWorkout> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val exArr = o.getJSONArray("exercises")
                val exercises = (0 until exArr.length()).map { j ->
                    val eo = exArr.getJSONObject(j)
                    val setArr = eo.getJSONArray("sets")
                    val sets = (0 until setArr.length()).map { k ->
                        val so = setArr.getJSONObject(k)
                        SetData(
                            prev = so.optString("prev", "—"),
                            weight = so.optString("weight", ""),
                            reps = so.optString("reps", ""),
                            done = so.optBoolean("done", false),
                        )
                    }
                    WorkoutExercise(
                        id = eo.optString("id"),
                        name = eo.optString("name"),
                        group = eo.optString("group"),
                        equip = eo.optString("equip"),
                        open = false,
                        sets = sets,
                    )
                }
                SavedWorkout(
                    id = o.getLong("id"),
                    title = o.optString("title"),
                    dateMillis = o.getLong("dateMillis"),
                    durationSec = o.optInt("durationSec"),
                    totalVolumeKg = o.optDouble("totalVolumeKg", 0.0),
                    totalSets = o.optInt("totalSets"),
                    exercises = exercises,
                    avgHeartRate = o.optInt("avgHeartRate", -1).takeIf { it > 0 },
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Build a [WorkoutSummaryData] (with PR detection) from a finished session. */
fun buildSummary(
    title: String,
    elapsedSec: Int,
    exercises: List<WorkoutExercise>,
): WorkoutSummaryData {
    var volume = 0.0
    var setCount = 0
    val log = mutableListOf<LogExercise>()
    val prs = mutableListOf<PrItem>()

    exercises.forEach { ex ->
        val doneSets = ex.sets.filter { it.done && it.weight.isNotBlank() && it.reps.isNotBlank() }
        doneSets.forEach { s ->
            val w = s.weight.toDoubleOrNull() ?: 0.0
            val r = s.reps.toIntOrNull() ?: 0
            volume += w * r
            setCount++
        }
        if (doneSets.isNotEmpty()) {
            log.add(
                LogExercise(
                    name = ex.name,
                    group = ex.group,
                    sets = doneSets.map { "${it.weight} × ${it.reps}" },
                )
            )
            // PR: heaviest weight this session vs. all-time best before this workout
            val sessionBest = doneSets.mapNotNull { it.weight.toDoubleOrNull() }.maxOrNull()
            val previousBest = WorkoutRepository.bestWeightFor(ex.name)
            if (sessionBest != null && (previousBest == null || sessionBest > previousBest)) {
                val bestSet = doneSets.maxByOrNull { it.weight.toDoubleOrNull() ?: 0.0 }!!
                val delta = if (previousBest != null)
                    "+${trimNum(sessionBest - previousBest)} kg" else "New"
                prs.add(
                    PrItem(
                        name = ex.name,
                        detail = "${bestSet.weight} kg × ${bestSet.reps}",
                        delta = delta,
                    )
                )
            }
        }
    }

    return WorkoutSummaryData(
        title = title,
        dur = formatClock(elapsedSec),
        sets = setCount,
        vol = String.format(java.util.Locale.US, "%,d", volume.toInt()),
        prs = prs,
        log = log,
    )
}

fun sessionVolume(exercises: List<WorkoutExercise>): Double =
    exercises.sumOf { ex ->
        ex.sets.filter { it.done }.sumOf { s ->
            (s.weight.toDoubleOrNull() ?: 0.0) * (s.reps.toIntOrNull() ?: 0)
        }
    }

fun sessionSetCount(exercises: List<WorkoutExercise>): Int =
    exercises.sumOf { ex -> ex.sets.count { it.done } }

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", v)
