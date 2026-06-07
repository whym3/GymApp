package com.example.gymapp

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

    /** Watch → phone: live on-wrist heart rate reading during a session, in BPM. */
    const val PATH_HEART_RATE = "/gymapp/heart-rate"

    /** Bare-bones mirror of the active session, just enough for an at-a-glance watch screen. */
    data class ActiveWorkoutSnapshot(
        val running: Boolean,
        val elapsedSec: Int,
        val exerciseName: String,
        val setProgress: String,
        val restSec: Int?,
    )

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
            .toString()

    fun decodeActiveWorkout(json: String): ActiveWorkoutSnapshot? = runCatching {
        val o = JSONObject(json)
        ActiveWorkoutSnapshot(
            running = o.getBoolean("running"),
            elapsedSec = o.getInt("elapsedSec"),
            exerciseName = o.getString("exerciseName"),
            setProgress = o.getString("setProgress"),
            restSec = if (o.isNull("restSec")) null else o.getInt("restSec"),
        )
    }.getOrNull()
}
