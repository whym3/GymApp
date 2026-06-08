package com.example.gymapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Formatters ────────────────────────────────────────────────────────────────
// Pure string formatting shared by phone and watch UIs so both render the
// same labels (clock, durations, volume, BMI category, …) consistently.

fun formatWorkoutDate(millis: Long): String {
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, then)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday"
    return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
}

fun formatDuration(sec: Int): String {
    val m = sec / 60
    return if (m < 60) "$m min"
    else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}

fun formatVolume(kg: Double): String =
    if (kg >= 1000) String.format(Locale.US, "%.1fk kg", kg / 1000) else "${kg.toInt()} kg"

/** Compact volume without unit, e.g. "8.2k" or "820". */
fun shortVolume(kg: Double): String =
    if (kg >= 1000) String.format(Locale.US, "%.1fk", kg / 1000) else kg.toInt().toString()

fun bmiCategory(bmi: Double): String = when {
    bmi < 18.5 -> "Underweight"
    bmi < 25.0 -> "Normal"
    bmi < 30.0 -> "Overweight"
    else -> "Obese"
}

fun formatClock(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
