package com.example.gymapp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format shared by the phone and watch over the Wearable Data Layer.
 * Kept dependency-free (plain JSON strings) so both sides can encode/decode
 * without either depending on the other's GMS wrapper code — the phone and
 * watch modules turn these strings into MessageClient/DataClient payloads.
 */
object WearSync {

    /** Watch → phone: request to start an empty workout session. No payload. */
    const val PATH_START_WORKOUT = "/gymapp/start-workout"

    /** Phone → watch: today's dashboard stats, pushed on every refresh. */
    const val PATH_TODAY_STATS = "/gymapp/today-stats"

    /** Phone → watch: live snapshot of the in-progress session, pushed on every change. */
    const val PATH_ACTIVE_WORKOUT = "/gymapp/active-workout"

    /** Watch → phone: pause/resume the running timer. No payload. */
    const val PATH_TOGGLE_PAUSE = "/gymapp/toggle-pause"

    /** Watch → phone: mark the current set done (and start the rest timer). No payload. */
    const val PATH_MARK_SET_DONE = "/gymapp/mark-set-done"

    /** Watch → phone: end the session and jump to the summary screen. No payload. */
    const val PATH_FINISH_WORKOUT = "/gymapp/finish-workout"

    /** Watch → phone: make the given exercise (by zero-based index) the remote's target. Payload: index as a UTF-8 string. */
    const val PATH_SELECT_EXERCISE = "/gymapp/select-exercise"

    /** Watch → phone: nudge the current set's weight by a signed delta, in kg. Payload: decimal string, e.g. "2.5" or "-2.5". */
    const val PATH_ADJUST_WEIGHT = "/gymapp/adjust-weight"

    /** Watch → phone: nudge the current set's rep count by a signed delta. Payload: integer string, e.g. "1" or "-1". */
    const val PATH_ADJUST_REPS = "/gymapp/adjust-reps"

    /** Watch → phone: append another set to the current exercise. No payload. */
    const val PATH_ADD_SET = "/gymapp/add-set"

    /** Watch → phone: extend the running rest timer (phone adds its usual 15s bump). No payload. */
    const val PATH_ADD_REST = "/gymapp/add-rest"

    /** Watch → phone: cancel the running rest timer early. No payload. */
    const val PATH_SKIP_REST = "/gymapp/skip-rest"

    /** Phone → watch: one-shot glanceable recap pushed the moment a session finishes. */
    const val PATH_WORKOUT_SUMMARY = "/gymapp/workout-summary"

    /** Watch → phone: live on-wrist heart rate reading during a session, in BPM. */
    const val PATH_HEART_RATE = "/gymapp/heart-rate"

    /** Phone → watch: lightweight list of past workouts for the watch's history browser, pushed whenever it changes. */
    const val PATH_WORKOUT_HISTORY = "/gymapp/workout-history"

    /** Watch → phone: ask for one saved workout's full detail, by id. Payload: id as a UTF-8 string. */
    const val PATH_REQUEST_WORKOUT_DETAIL = "/gymapp/request-workout-detail"

    /** Phone → watch: one saved workout's full detail, sent in reply to [PATH_REQUEST_WORKOUT_DETAIL]. */
    const val PATH_WORKOUT_DETAIL = "/gymapp/workout-detail"

    /**
     * Mirror of the active session for the watch screen. [exercises] lists every
     * exercise's name so the watch can offer a picker; [currentExerciseIndex]
     * is which of those the remote currently targets (mark-done / weight / reps
     * / add-set all act on it), and [currentWeight]/[currentReps] are that
     * target set's editable values, shown next to the +/- adjust controls.
     */
    data class ActiveWorkoutSnapshot(
        val running: Boolean,
        val elapsedSec: Int,
        val exerciseName: String,
        val setProgress: String,
        val restSec: Int?,
        val exercises: List<String> = emptyList(),
        val currentExerciseIndex: Int = 0,
        val currentWeight: String = "",
        val currentReps: String = "",
    )

    /** Glanceable recap shown on the watch right after a session ends, before it returns to idle. */
    data class WorkoutSummary(
        val durationSec: Int,
        val totalSets: Int,
        val totalVolumeKg: Int,
    )

    /** Lightweight stand-in for [SavedWorkout] sized for the watch's scrollable history list — no per-set detail. */
    data class WorkoutHistoryEntry(
        val id: Long,
        val title: String,
        val dateMillis: Long,
        val durationSec: Int,
        val totalVolumeKg: Double,
        val totalSets: Int,
        val muscleGroups: List<String>,
    )

    fun encodeWorkoutHistory(entries: List<WorkoutHistoryEntry>): String =
        JSONArray().apply {
            entries.forEach { e ->
                put(
                    JSONObject()
                        .put("id", e.id)
                        .put("title", e.title)
                        .put("dateMillis", e.dateMillis)
                        .put("durationSec", e.durationSec)
                        .put("totalVolumeKg", e.totalVolumeKg)
                        .put("totalSets", e.totalSets)
                        .put("muscleGroups", JSONArray(e.muscleGroups))
                )
            }
        }.toString()

    fun decodeWorkoutHistory(json: String): List<WorkoutHistoryEntry> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val groups = o.optJSONArray("muscleGroups")
            WorkoutHistoryEntry(
                id = o.getLong("id"),
                title = o.getString("title"),
                dateMillis = o.getLong("dateMillis"),
                durationSec = o.getInt("durationSec"),
                totalVolumeKg = o.getDouble("totalVolumeKg"),
                totalSets = o.getInt("totalSets"),
                muscleGroups = if (groups != null) List(groups.length()) { groups.getString(it) } else emptyList(),
            )
        }
    }.getOrDefault(emptyList())

    fun encodeId(id: Long): ByteArray = id.toString().toByteArray(Charsets.UTF_8)

    fun decodeId(bytes: ByteArray): Long? = String(bytes, Charsets.UTF_8).toLongOrNull()

    /** Full detail for one saved workout, including every exercise and set — sent on demand, not pushed proactively. */
    fun encodeSavedWorkout(w: SavedWorkout): String =
        JSONObject()
            .put("id", w.id)
            .put("title", w.title)
            .put("dateMillis", w.dateMillis)
            .put("durationSec", w.durationSec)
            .put("totalVolumeKg", w.totalVolumeKg)
            .put("totalSets", w.totalSets)
            .put("avgHeartRate", w.avgHeartRate ?: JSONObject.NULL)
            .put(
                "exercises",
                JSONArray().apply {
                    w.exercises.forEach { ex ->
                        put(
                            JSONObject()
                                .put("id", ex.id)
                                .put("name", ex.name)
                                .put("group", ex.group)
                                .put(
                                    "sets",
                                    JSONArray().apply {
                                        ex.sets.forEach { s ->
                                            put(JSONObject().put("weight", s.weight).put("reps", s.reps))
                                        }
                                    },
                                )
                        )
                    }
                },
            )
            .toString()

    fun decodeSavedWorkout(json: String): SavedWorkout? = runCatching {
        val o = JSONObject(json)
        val exArr = o.getJSONArray("exercises")
        val exercises = (0 until exArr.length()).map { i ->
            val eo = exArr.getJSONObject(i)
            val setArr = eo.getJSONArray("sets")
            WorkoutExercise(
                id = eo.optString("id"),
                name = eo.getString("name"),
                group = eo.getString("group"),
                equip = "",
                sets = (0 until setArr.length()).map { j ->
                    val so = setArr.getJSONObject(j)
                    SetData(prev = "—", weight = so.optString("weight", ""), reps = so.optString("reps", ""), done = true)
                },
            )
        }
        SavedWorkout(
            id = o.getLong("id"),
            title = o.getString("title"),
            dateMillis = o.getLong("dateMillis"),
            durationSec = o.getInt("durationSec"),
            totalVolumeKg = o.getDouble("totalVolumeKg"),
            totalSets = o.getInt("totalSets"),
            exercises = exercises,
            avgHeartRate = if (o.isNull("avgHeartRate")) null else o.getInt("avgHeartRate"),
        )
    }.getOrNull()

    fun encodeWorkoutSummary(s: WorkoutSummary): String =
        JSONObject()
            .put("durationSec", s.durationSec)
            .put("totalSets", s.totalSets)
            .put("totalVolumeKg", s.totalVolumeKg)
            .toString()

    fun decodeWorkoutSummary(json: String): WorkoutSummary? = runCatching {
        val o = JSONObject(json)
        WorkoutSummary(
            durationSec = o.optInt("durationSec"),
            totalSets = o.optInt("totalSets"),
            totalVolumeKg = o.optInt("totalVolumeKg"),
        )
    }.getOrNull()

    fun encodeTodayStats(stats: TodayStats): String =
        JSONObject()
            .put("steps", stats.steps)
            .put("calories", stats.calories)
            .put("heartRate", stats.heartRate)
            .toString()

    fun decodeTodayStats(json: String): TodayStats? = runCatching {
        val o = JSONObject(json)
        TodayStats(
            steps = o.getString("steps"),
            calories = o.getString("calories"),
            heartRate = o.getString("heartRate"),
        )
    }.getOrNull()

    fun encodeHeartRate(bpm: Int): ByteArray = bpm.toString().toByteArray(Charsets.UTF_8)

    fun decodeHeartRate(bytes: ByteArray): Int? = String(bytes, Charsets.UTF_8).toIntOrNull()

    fun encodeActiveWorkout(s: ActiveWorkoutSnapshot): String =
        JSONObject()
            .put("running", s.running)
            .put("elapsedSec", s.elapsedSec)
            .put("exerciseName", s.exerciseName)
            .put("setProgress", s.setProgress)
            .put("restSec", s.restSec ?: JSONObject.NULL)
            .put("exercises", JSONArray(s.exercises))
            .put("currentExerciseIndex", s.currentExerciseIndex)
            .put("currentWeight", s.currentWeight)
            .put("currentReps", s.currentReps)
            .toString()

    fun decodeActiveWorkout(json: String): ActiveWorkoutSnapshot? = runCatching {
        val o = JSONObject(json)
        val names = o.optJSONArray("exercises")
        ActiveWorkoutSnapshot(
            running = o.getBoolean("running"),
            elapsedSec = o.getInt("elapsedSec"),
            exerciseName = o.getString("exerciseName"),
            setProgress = o.getString("setProgress"),
            restSec = if (o.isNull("restSec")) null else o.getInt("restSec"),
            exercises = if (names != null) List(names.length()) { names.getString(it) } else emptyList(),
            currentExerciseIndex = o.optInt("currentExerciseIndex", 0),
            currentWeight = o.optString("currentWeight", ""),
            currentReps = o.optString("currentReps", ""),
        )
    }.getOrNull()

    fun encodeIndex(index: Int): ByteArray = index.toString().toByteArray(Charsets.UTF_8)

    fun decodeIndex(bytes: ByteArray): Int? = String(bytes, Charsets.UTF_8).toIntOrNull()

    fun encodeDelta(delta: Number): ByteArray = delta.toString().toByteArray(Charsets.UTF_8)

    fun decodeWeightDelta(bytes: ByteArray): Double? = String(bytes, Charsets.UTF_8).toDoubleOrNull()

    fun decodeRepsDelta(bytes: ByteArray): Int? = String(bytes, Charsets.UTF_8).toDoubleOrNull()?.toInt()
}
