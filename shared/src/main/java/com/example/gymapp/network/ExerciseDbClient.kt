package com.example.gymapp.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Client for the public ExerciseDB API (https://oss.exercisedb.dev) — used to look up GIF demos for exercises. */
object ExerciseDbClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: ExerciseDbApi = Retrofit.Builder()
        .baseUrl("https://oss.exercisedb.dev/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ExerciseDbApi::class.java)

    private val cache = ConcurrentHashMap<String, java.util.Optional<ExerciseDbExercise>>()

    /**
     * Curated overrides for exercises whose ExerciseDB `?name=` search returns no good
     * candidates (the search is a fuzzy/OR word match and often misses the right exercise
     * entirely within the first 25 results), found via manual lookup of the correct
     * `exerciseId`.
     */
    private val idOverrides = mapOf(
        "Barbell Row" to "eZyBC3j",       // barbell bent over row
        "Dumbbell Row" to "BJ0Hz5L",      // dumbbell bent over row
        "Seated Cable Row" to "7I6LNUG",  // lever seated row
        "Leg Extension" to "my33uHU",     // lever leg extension
        "Face Pulls" to "wqNPGCg",        // cable rear delt row (with rope)
        "Rear Delt Fly" to "mu5Guxt",     // dumbbell rear delt raise
        "V-up" to "mbkgB44",              // jackknife sit-up
        "Cable Crunch" to "8xUv4J7",      // cable seated crunch
    )

    /** Minimum [score] for a search-based match to be trusted; below this, no demo is shown. */
    private const val MIN_SCORE = 4.0

    /**
     * Best-effort lookup of an ExerciseDB demo (GIF + instructions) matching [exerciseName].
     * Known-bad search results are corrected via [idOverrides]. Otherwise the ExerciseDB
     * `?name=` search (a fuzzy/OR match across words that often returns unrelated results
     * first) is re-ranked using [group] (mapped to ExerciseDB body parts) and [equip]
     * (mapped to ExerciseDB equipment) plus name word overlap; if the best candidate scores
     * below [MIN_SCORE], no demo is returned rather than showing an unrelated match.
     */
    suspend fun findDemo(exerciseName: String, group: String, equip: String): ExerciseDbExercise? {
        cache[exerciseName]?.let { return it.orElse(null) }

        val demo = runCatching {
            idOverrides[exerciseName]?.let { id -> return@runCatching api.getExercise(id).data }

            val cleaned = exerciseName.replace(Regex("\\(.*?\\)"), "").trim()
            val queryTokens = tokenize(exerciseName)
            val bodyParts = bodyPartsFor(group)
            val equipments = equipmentsFor(equip)

            var results = api.searchExercises(name = cleaned.ifBlank { exerciseName }, limit = 25).data
            if (results.isEmpty()) {
                val lastWord = cleaned.substringAfterLast(' ')
                if (!lastWord.equals(cleaned, ignoreCase = true)) {
                    results = api.searchExercises(name = lastWord, limit = 25).data
                }
            }
            results.map { it to score(it, queryTokens, bodyParts, equipments) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= MIN_SCORE }
                ?.first
        }.getOrNull()

        cache[exerciseName] = java.util.Optional.ofNullable(demo)
        return demo
    }

    private fun score(candidate: ExerciseDbExercise, queryTokens: List<String>, bodyParts: Set<String>, equipments: Set<String>): Double {
        var s = 0.0
        if (candidate.bodyParts.any { it in bodyParts }) s += 3
        if (candidate.equipments.any { it in equipments }) s += 2
        val candidateTokens = tokenize(candidate.name)
        val overlap = queryTokens.count { it in candidateTokens }
        s += overlap
        s -= 0.1 * (candidateTokens.size - overlap)
        return s
    }

    /** Splits a name into lowercase word tokens, expanding compounds like "pullup"/"pushups" -> "pull"/"up". */
    private fun tokenize(name: String): List<String> {
        val cleaned = name.lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace("-", " ")
        return cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .flatMap { word ->
                when {
                    word.length > 3 && word.endsWith("ups") -> listOf(word.dropLast(3), "up")
                    word.length > 2 && word.endsWith("up") -> listOf(word.dropLast(2), "up")
                    else -> listOf(word)
                }
            }
    }

    private fun bodyPartsFor(group: String): Set<String> = when (group) {
        "Chest" -> setOf("chest")
        "Back" -> setOf("back")
        "Legs" -> setOf("upper legs", "lower legs")
        "Shoulders" -> setOf("shoulders")
        "Arms" -> setOf("upper arms", "lower arms")
        "Core" -> setOf("waist")
        else -> emptySet()
    }

    private fun equipmentsFor(equip: String): Set<String> = when (equip) {
        "Barbell" -> setOf("barbell", "ez barbell", "olympic barbell", "trap bar")
        "Dumbbell" -> setOf("dumbbell")
        "Machine" -> setOf("leverage machine", "smith machine", "cable", "sled machine")
        "Bodyweight" -> setOf("body weight")
        else -> emptySet()
    }
}
