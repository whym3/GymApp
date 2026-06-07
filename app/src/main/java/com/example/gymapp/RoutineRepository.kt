package com.example.gymapp

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
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

/** Persists user routines as JSON; exposes a Compose snapshot list. */
object RoutineRepository {

    private val _routines = mutableStateListOf<Routine>()
    val routines: List<Routine> get() = _routines

    fun init(context: Context) {
        reload(context)
    }

    /** (Re)load the current account's routines from disk. */
    fun reload(context: Context) {
        _routines.clear()
        _routines.addAll(readFromDisk(context))
    }

    fun add(context: Context, routine: Routine) {
        _routines.add(0, routine)
        writeToDisk(context)
    }

    fun delete(context: Context, id: Long) {
        _routines.removeAll { it.id == id }
        writeToDisk(context)
    }

    fun byId(id: Long): Routine? = _routines.firstOrNull { it.id == id }

    private fun file(context: Context): File {
        val id = UserStore.accountId.ifBlank { "default" }
        return File(context.filesDir, "routines_$id.json")
    }

    private fun writeToDisk(context: Context) {
        val arr = JSONArray()
        _routines.forEach { r ->
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
        runCatching { file(context).writeText(arr.toString()) }
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
