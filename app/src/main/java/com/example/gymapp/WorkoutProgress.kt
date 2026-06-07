package com.example.gymapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirrors the in-progress session's exercise/set breakdown so [WorkoutService]
 * can build a rich progress notification without depending on Compose state.
 * [MainActivity] republishes this on every change to `exercises`, the same way
 * it mirrors the session to the Wear OS companion via `activeSnapshot`.
 */
object WorkoutProgress {

    data class ExerciseProgress(val name: String, val totalSets: Int, val doneSets: Int)

    data class State(
        val exercises: List<ExerciseProgress> = emptyList(),
        val currentExerciseIndex: Int = 0,
    ) {
        val totalSets: Int get() = exercises.sumOf { it.totalSets }
        val doneSets: Int get() = exercises.sumOf { it.doneSets }
        val currentExerciseName: String? get() = exercises.getOrNull(currentExerciseIndex)?.name
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(exercises: List<ExerciseProgress>, currentExerciseIndex: Int) {
        _state.value = State(exercises, currentExerciseIndex)
    }

    fun clear() {
        _state.value = State()
    }
}
