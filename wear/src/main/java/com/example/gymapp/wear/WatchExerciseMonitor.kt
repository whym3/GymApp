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
            metrics.getData(DataType.STEPS_TOTAL)?.let { _steps.value = it.total }
            metrics.getData(DataType.CALORIES_TOTAL)?.let { _calories.value = it.total }
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
        val config = ExerciseConfig(
            exerciseType = ExerciseType.STRENGTH_TRAINING,
            dataTypes = setOf(DataType.HEART_RATE_BPM, DataType.STEPS_TOTAL, DataType.CALORIES_TOTAL),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false,
        )
        c.startExerciseAsync(config).logFailure("startExercise")
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
