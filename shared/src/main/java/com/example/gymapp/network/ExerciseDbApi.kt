package com.example.gymapp.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExerciseDbApi {
    @GET("api/v1/exercises")
    suspend fun searchExercises(
        @Query("name") name: String,
        @Query("limit") limit: Int = 5,
    ): ExerciseDbListResponse

    @GET("api/v1/exercises/{exerciseId}")
    suspend fun getExercise(@Path("exerciseId") exerciseId: String): ExerciseDbDetailResponse
}
