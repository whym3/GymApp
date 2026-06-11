package com.example.gymapp.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives a Health Services *exercise* session in lockstep with the phone's
 * workout, streaming live on-wrist heart rate plus session-cumulative steps
 * and calories.
 *
 * This is the only live path for in-workout steps/kcal: passive monitoring
 * (see [WatchActivityMonitor]) batches daily totals with unbounded report
 * latency, so mid-session its values just sit frozen. The exercise also
 * delivers HR continuously, replacing a separate MeasureClient subscription.
 */
object WatchExerciseMonitor {

    private const val TAG = "WatchExerciseMonitor"

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    /** Steps taken since the exercise started — already session-relative, no baseline math. */
    private val _steps = MutableStateFlow<Long?>(null)
    val steps: StateFlow<Long?> = _steps.asStateFlow()

    private val _calories = MutableStateFlow<Double?>(null)
    val calories: StateFlow<Double?> = _calories.asStateFlow()

    private var started = false
    private var pausedByUs = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics
            metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { sample ->
                val bpm = sample.value.toInt()
                if (bpm > 0) _heartRate.value = bpm
            }
            // Prefer the device-computed session totals; accumulate the delta
            // samples ourselves on devices that don't offer them (see start()).
            val totalSteps = metrics.getData(DataType.STEPS_TOTAL)
            if (totalSteps != null) {
                _steps.value = totalSteps.total
            } else {
                val delta = metrics.getData(DataType.STEPS).sumOf { it.value }
                if (delta > 0) _steps.value = (_steps.value ?: 0L) + delta
            }
            val totalCalories = metrics.getData(DataType.CALORIES_TOTAL)
            if (totalCalories != null) {
                _calories.value = totalCalories.total
            } else {
                val delta = metrics.getData(DataType.CALORIES).sumOf { it.value }
                if (delta > 0) _calories.value = (_calories.value ?: 0.0) + delta
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

        override fun onRegistered() {}

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "exercise callback registration failed", throwable)
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    private fun client(context: Context) =
        HealthServices.getClient(context.applicationContext).exerciseClient

    fun start(context: Context) {
        if (started) return
        started = true
        pausedByUs = false
        _heartRate.value = null
        _steps.value = null
        _calories.value = null
        val c = client(context)
        c.setUpdateCallback(callback)
        // Capability sets differ per watch (this one rejects Steps for
        // STRENGTH_TRAINING), so resolve what to request at runtime: prefer
        // strength training, fall back to the generic workout type if it
        // covers more of HR/steps/calories, and take delta types where the
        // session totals aren't offered.
        val capFuture = c.getCapabilitiesAsync()
        capFuture.addListener(
            {
                runCatching {
                    val caps = capFuture.get()
                    var best: Pair<ExerciseType, Set<DataType<*, *>>>? = null
                    for (type in listOf(ExerciseType.STRENGTH_TRAINING, ExerciseType.WORKOUT)) {
                        val supported = runCatching {
                            caps.getExerciseTypeCapabilities(type).supportedDataTypes
                        }.getOrNull() ?: continue
                        val request = buildSet {
                            if (DataType.HEART_RATE_BPM in supported) add(DataType.HEART_RATE_BPM)
                            if (DataType.STEPS_TOTAL in supported) add(DataType.STEPS_TOTAL)
                            else if (DataType.STEPS in supported) add(DataType.STEPS)
                            if (DataType.CALORIES_TOTAL in supported) add(DataType.CALORIES_TOTAL)
                            else if (DataType.CALORIES in supported) add(DataType.CALORIES)
                        }
                        if (best == null || request.size > best.second.size) best = type to request
                        if (request.size == 3) break   // first type covering everything wins
                    }
                    val (type, dataTypes) = best ?: error("no usable exercise type on this device")
                    Log.i(TAG, "starting $type with $dataTypes")
                    val startFuture = c.startExerciseAsync(
                        ExerciseConfig(
                            exerciseType = type,
                            dataTypes = dataTypes,
                            isAutoPauseAndResumeEnabled = false,
                            isGpsEnabled = false,
                        ),
                    )
                    startFuture.addListener(
                        {
                            runCatching { startFuture.get() }.onFailure { e ->
                                val alreadyActive = generateSequence(e as Throwable?) { it.cause }
                                    .any { it.message?.contains("already", ignoreCase = true) == true }
                                if (alreadyActive) {
                                    // Process restarted mid-session: the exercise we
                                    // started earlier is still running and the callback
                                    // re-registration above reattaches us to it.
                                    Log.w(TAG, "exercise already active — reattached")
                                } else {
                                    // Roll back so the session loop retries.
                                    started = false
                                    Log.e(TAG, "startExercise failed", e)
                                }
                            }
                        },
                        MoreExecutors.directExecutor(),
                    )
                }.onFailure {
                    started = false
                    Log.e(TAG, "capability query failed", it)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Pause sensor aggregation while the phone timer is paused. */
    fun pause(context: Context) {
        if (!started || pausedByUs) return
        pausedByUs = true
        client(context).pauseExerciseAsync().logFailure("pauseExercise")
    }

    fun resume(context: Context) {
        if (!started || !pausedByUs) return
        pausedByUs = false
        client(context).resumeExerciseAsync().logFailure("resumeExercise")
    }

    fun stop(context: Context) {
        if (!started) return
        started = false
        pausedByUs = false
        val c = client(context)
        c.endExerciseAsync().logFailure("endExercise")
        c.clearUpdateCallbackAsync(callback).logFailure("clearUpdateCallback")
        _heartRate.value = null
        _steps.value = null
        _calories.value = null
    }

    private fun <T> ListenableFuture<T>.logFailure(op: String) {
        addListener(
            { runCatching { get() }.onFailure { Log.e(TAG, "$op failed", it) } },
            MoreExecutors.directExecutor(),
        )
    }
}
