package com.example.gymapp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Crash/process-death insurance for the in-progress workout. The active session
 * lives only in Compose state; if Android kills the app mid-workout, this
 * journal is what brings it back. [save] runs on every meaningful session
 * change (the caller debounces), [restore] once at startup, [clear] whenever
 * the session ends normally.
 */
object SessionJournal {

    data class Restored(
        val title: String,
        val startMillis: Long,
        val elapsedSec: Int,
        val running: Boolean,
        val exercises: List<WorkoutExercise>,
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** Don't credit more than this much "still running" wall time to a dead
     *  session — past it, restore paused instead of showing a days-long timer. */
    private const val MAX_RUNNING_CREDIT_SEC = 12 * 3600

    private fun file(context: Context) = File(context.filesDir, "active_session.json")

    fun save(
        context: Context,
        title: String,
        startMillis: Long,
        elapsedSec: Int,
        running: Boolean,
        exercises: List<WorkoutExercise>,
    ) {
        val f = file(context.applicationContext)
        val json = encode(title, startMillis, elapsedSec, running, exercises)
        ioScope.launch { writeMutex.withLock { runCatching { f.writeText(json) } } }
    }

    fun clear(context: Context) {
        val f = file(context.applicationContext)
        ioScope.launch { writeMutex.withLock { runCatching { f.delete() } } }
    }

    /** Read + decode the journal, or null if none/corrupt. Call from a background dispatcher. */
    fun restore(context: Context): Restored? {
        val f = file(context.applicationContext)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val exArr = o.getJSONArray("exercises")
            val savedAt = o.getLong("savedAtMillis")
            val wasRunning = o.getBoolean("running")
            val baseElapsed = o.getInt("elapsedSec")
            // A session that was running when the process died kept accruing
            // wall-clock time we couldn't tick — credit it, within reason.
            val credit = ((System.currentTimeMillis() - savedAt) / 1000L).toInt().coerceAtLeast(0)
            val (elapsed, running) =
                if (wasRunning && credit <= MAX_RUNNING_CREDIT_SEC) (baseElapsed + credit) to true
                else baseElapsed to false
            Restored(
                title = o.optString("title", "Workout"),
                startMillis = o.getLong("startMillis"),
                elapsedSec = elapsed,
                running = running,
                exercises = (0 until exArr.length()).map { i -> decodeExercise(exArr.getJSONObject(i)) },
            )
        }.getOrNull()
    }

    private fun encode(
        title: String,
        startMillis: Long,
        elapsedSec: Int,
        running: Boolean,
        exercises: List<WorkoutExercise>,
    ): String {
        val exArr = JSONArray()
        exercises.forEach { ex ->
            val setArr = JSONArray()
            ex.sets.forEach { s ->
                setArr.put(
                    JSONObject()
                        .put("prev", s.prev)
                        .put("weight", s.weight)
                        .put("reps", s.reps)
                        .put("done", s.done)
                        .put("type", s.type.name)
                )
            }
            exArr.put(
                JSONObject()
                    .put("id", ex.id)
                    .put("name", ex.name)
                    .put("group", ex.group)
                    .put("equip", ex.equip)
                    .put("open", ex.open)
                    .put("sets", setArr)
            )
        }
        return JSONObject()
            .put("title", title)
            .put("startMillis", startMillis)
            .put("elapsedSec", elapsedSec)
            .put("running", running)
            .put("savedAtMillis", System.currentTimeMillis())
            .put("exercises", exArr)
            .toString()
    }

    private fun decodeExercise(eo: JSONObject): WorkoutExercise {
        val setArr = eo.getJSONArray("sets")
        return WorkoutExercise(
            id = eo.optString("id"),
            name = eo.optString("name"),
            group = eo.optString("group"),
            equip = eo.optString("equip"),
            open = eo.optBoolean("open", true),
            sets = (0 until setArr.length()).map { k ->
                val so = setArr.getJSONObject(k)
                SetData(
                    prev = so.optString("prev", "—"),
                    weight = so.optString("weight", ""),
                    reps = so.optString("reps", ""),
                    done = so.optBoolean("done", false),
                    type = runCatching { SetType.valueOf(so.optString("type", "NORMAL")) }
                        .getOrDefault(SetType.NORMAL),
                )
            },
        )
    }
}
