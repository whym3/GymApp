package com.example.gymapp

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

/**
 * Persistent user profile. Holds the account (id / name / email / body metrics /
 * birthday / photo) plus two flags:
 *  - [hasAccount]: a profile has been created on this device (survives logout).
 *  - [loggedIn]:   a session is currently active (cleared on logout).
 *
 * Bodyweight is tracked here (not Health Connect): each time the profile weight
 * changes a point is appended to [weightHistory], which feeds the Progress graph.
 */
object UserStore {

    private var prefs: SharedPreferences? = null

    var accountId by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var heightCm by mutableStateOf<Int?>(null)
        private set
    var weightKg by mutableStateOf<Double?>(null)
        private set
    var birthdayMillis by mutableStateOf<Long?>(null)
        private set
    var photoPath by mutableStateOf<String?>(null)
        private set
    var weightHistory by mutableStateOf<List<Pair<Long, Double>>>(emptyList())
        private set
    var hasAccount by mutableStateOf(false)
        private set
    var loggedIn by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("gymlog_user", Context.MODE_PRIVATE)
        prefs = p
        accountId = p.getString("accountId", "") ?: ""
        name = p.getString("name", "") ?: ""
        email = p.getString("email", "") ?: ""
        heightCm = p.getInt("heightCm", -1).takeIf { it > 0 }
        weightKg = p.getFloat("weightKg", -1f).takeIf { it > 0f }?.toDouble()
        birthdayMillis = p.getLong("birthdayMillis", -1L).takeIf { it > 0L }
        photoPath = p.getString("photoPath", null)?.takeIf { it.isNotBlank() }
        weightHistory = parseHistory(p.getString("weightHistory", "") ?: "")
        hasAccount = p.getBoolean("hasAccount", false)
        loggedIn = p.getBoolean("loggedIn", false)

        // Seed a first weight point for existing profiles that have a weight but no history.
        if (weightHistory.isEmpty() && weightKg != null) {
            weightHistory = listOf(System.currentTimeMillis() to weightKg!!)
        }
    }

    fun createAccount(name: String, email: String) {
        this.accountId = UUID.randomUUID().toString()
        this.name = name.trim()
        this.email = email.trim()
        this.heightCm = null
        this.weightKg = null
        this.birthdayMillis = null
        this.photoPath = null
        this.weightHistory = emptyList()
        this.hasAccount = true
        this.loggedIn = true
        persist()
    }

    fun login() { loggedIn = true; persist() }

    fun logout() { loggedIn = false; persist() }

    fun deleteAccount() {
        accountId = ""
        name = ""; email = ""; heightCm = null; weightKg = null
        birthdayMillis = null; photoPath = null; weightHistory = emptyList()
        hasAccount = false; loggedIn = false
        persist()
    }

    fun updateProfile(
        name: String,
        email: String,
        heightCm: Int?,
        weightKg: Double?,
        birthdayMillis: Long?,
    ) {
        this.name = name.trim()
        this.email = email.trim()
        this.heightCm = heightCm?.takeIf { it > 0 }
        this.birthdayMillis = birthdayMillis?.takeIf { it > 0 }
        val newWeight = weightKg?.takeIf { it > 0 }
        // Append to history only when the weight actually changes.
        if (newWeight != null && weightHistory.lastOrNull()?.second != newWeight) {
            weightHistory = weightHistory + (System.currentTimeMillis() to newWeight)
        }
        this.weightKg = newWeight
        persist()
    }

    fun setPhoto(path: String?) {
        photoPath = path?.takeIf { it.isNotBlank() }
        persist()
    }

    private fun persist() {
        prefs?.edit()
            ?.putString("accountId", accountId)
            ?.putString("name", name)
            ?.putString("email", email)
            ?.putInt("heightCm", heightCm ?: -1)
            ?.putFloat("weightKg", weightKg?.toFloat() ?: -1f)
            ?.putLong("birthdayMillis", birthdayMillis ?: -1L)
            ?.putString("photoPath", photoPath ?: "")
            ?.putString("weightHistory", historyJson())
            ?.putBoolean("hasAccount", hasAccount)
            ?.putBoolean("loggedIn", loggedIn)
            ?.apply()
    }

    private fun historyJson(): String {
        val arr = JSONArray()
        weightHistory.forEach { (t, w) -> arr.put(JSONObject().put("t", t).put("w", w)) }
        return arr.toString()
    }

    private fun parseHistory(json: String): List<Pair<Long, Double>> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getLong("t") to o.getDouble("w")
            }.sortedBy { it.first }
        }.getOrDefault(emptyList())
    }

    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "A"
    val displayName: String get() = name.ifBlank { "Athlete" }

    /** Age in whole years from the birthday, or null. */
    val age: Int?
        get() = birthdayMillis?.let {
            val dob = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            Period.between(dob, LocalDate.now()).years.takeIf { y -> y in 0..130 }
        }

    /** Current BMI from weight + height, or null. */
    val bmi: Double?
        get() {
            val w = weightKg ?: return null
            val h = heightCm ?: return null
            if (h <= 0) return null
            val m = h / 100.0
            return w / (m * m)
        }
}
