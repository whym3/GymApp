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
}
