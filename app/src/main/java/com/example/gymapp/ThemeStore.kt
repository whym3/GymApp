package com.example.gymapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Persisted appearance preference: Light, Dark or follow System. */
object ThemeStore {

    enum class Mode { LIGHT, DARK, SYSTEM }

    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(Mode.DARK)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("gymlog_theme", Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { Mode.valueOf(p.getString("mode", Mode.DARK.name)!!) }.getOrDefault(Mode.DARK)
    }

    fun set(newMode: Mode) {
        mode = newMode
        prefs?.edit()?.putString("mode", newMode.name)?.apply()
    }

    val label: String get() = when (mode) {
        Mode.LIGHT -> "Light"
        Mode.DARK -> "Dark"
        Mode.SYSTEM -> "System"
    }
}
