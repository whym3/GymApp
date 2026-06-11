package com.example.gymapp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A user-created workout schedule / routine. */
data class Routine(
    val id: Long,
    val name: String,
    val exercises: List<ExerciseLibraryItem>,
) {
    val groups: List<String> get() = exercises.map { it.group }.distinct()

    /** Adapt to a [WorkoutTemplate] so it can reuse the template-detail / start flow. */
    fun toTemplate(): WorkoutTemplate = WorkoutTemplate(
        id = "routine-$id",
        name = name,
        subtitle = groups.joinToString(" · "),
        description = "Your custom routine — ${exercises.size} exercise" +
            (if (exercises.size == 1) "" else "s") +
            " across ${groups.size} muscle group" + (if (groups.size == 1) "" else "s") + ".",
        exercises = exercises,
    )
}

/**
 * Persists user routines as JSON; exposes a Compose snapshot list. Same
 * threading contract as [WorkoutRepository]: in-memory mutations are immediate,
 * file writes happen on IO behind a mutex.
 */
object RoutineRepository {

    private val _routines = mutableStateListOf<Routine>()
    val routines: List<Routine> get() = _routines

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun init(context: Context) {
        // Blocking on purpose: runs once in onCreate and the first frame needs the data.
        _routines.clear()
        _routines.addAll(readFromDisk(context))
    }

    /** (Re)load the current account's routines from disk. */
    suspend fun reload(context: Context) {
        val items = withContext(Dispatchers.IO) { readFromDisk(context) }
        _routines.clear()
        _routines.addAll(items)
    }

    fun add(context: Context, routine: Routine) {
        _routines.add(0, routine)
        persistAsync(context)
    }

    fun delete(context: Context, id: Long) {
        _routines.removeAll { it.id == id }
        persistAsync(context)
    }

    fun update(context: Context, routine: Routine) {
        val i = _routines.indexOfFirst { it.id == routine.id }
        if (i >= 0) { _routines[i] = routine; persistAsync(context) }
    }

    fun deleteAll(context: Context) {
        _routines.clear()
        val f = file(context)
        ioScope.launch { writeMutex.withLock { runCatching { f.delete() } } }
    }

    /** Snapshot the list + target file now (account may switch later), write on IO. */
    private fun persistAsync(context: Context) {
        val snapshot = _routines.toList()
        val f = file(context)
        ioScope.launch {
            writeMutex.withLock {
                runCatching { f.writeText(encode(snapshot)) }
            }
        }
    }

    fun byId(id: Long): Routine? = _routines.firstOrNull { it.id == id }

    private fun file(context: Context): File {
        val id = UserStore.accountId.ifBlank { "default" }
        return File(context.filesDir, "routines_$id.json")
    }

    private fun encode(routines: List<Routine>): String {
        val arr = JSONArray()
        routines.forEach { r ->
            val exArr = JSONArray()
            r.exercises.forEach { ex ->
                exArr.put(
                    JSONObject()
                        .put("name", ex.name)
                        .put("group", ex.group)
                        .put("equip", ex.equip)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("exercises", exArr)
            )
        }
        return arr.toString()
    }

    private fun readFromDisk(context: Context): List<Routine> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val exArr = o.getJSONArray("exercises")
                val exercises = (0 until exArr.length()).map { j ->
                    val eo = exArr.getJSONObject(j)
                    ExerciseLibraryItem(
                        name = eo.optString("name"),
                        group = eo.optString("group"),
                        equip = eo.optString("equip"),
                    )
                }
                Routine(id = o.getLong("id"), name = o.optString("name"), exercises = exercises)
            }
        }.getOrDefault(emptyList())
    }
}
