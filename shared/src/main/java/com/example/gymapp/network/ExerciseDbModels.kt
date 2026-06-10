package com.example.gymapp.network

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseDbExercise(
    val exerciseId: String,
    val name: String,
    val gifUrl: String,
    val bodyParts: List<String> = emptyList(),
    val equipments: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
)

@Serializable
data class ExerciseDbListResponse(
    val success: Boolean = false,
    val data: List<ExerciseDbExercise> = emptyList(),
)

@Serializable
data class ExerciseDbDetailResponse(
    val success: Boolean = false,
    val data: ExerciseDbExercise? = null,
)
