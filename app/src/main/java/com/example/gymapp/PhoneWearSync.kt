package com.example.gymapp

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remote-control commands the watch can send while a workout is in progress. */
enum class ActiveWorkoutCommand { TOGGLE_PAUSE, MARK_SET_DONE, FINISH_WORKOUT, ADD_SET, ADD_REST, SKIP_REST }

/**
 * Phone side of the Wearable Data Layer bridge to the watch companion.
 * Receives "start workout" requests from the watch (mirrored into
 * [startWorkoutRequested], the same pattern as [WorkoutTimer.finishRequested]),
 * forwards in-session remote-control taps via [activeWorkoutCommands], and
 * pushes [TodayStats]/[WearSync.ActiveWorkoutSnapshot] out to the watch.
 */
object PhoneWearSync {

    private const val TAG = "PhoneWearSync"

    private val _startWorkoutRequested = MutableStateFlow(false)
    val startWorkoutRequested: StateFlow<Boolean> = _startWorkoutRequested.asStateFlow()

    private val _activeWorkoutCommands = MutableSharedFlow<ActiveWorkoutCommand>(extraBufferCapacity = 4)
    val activeWorkoutCommands: SharedFlow<ActiveWorkoutCommand> = _activeWorkoutCommands.asSharedFlow()

    /** Watch picked a different exercise (zero-based index into the session's exercise list) to remote-control. */
    private val _selectExerciseRequests = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val selectExerciseRequests: SharedFlow<Int> = _selectExerciseRequests.asSharedFlow()

    /** Watch nudged the current set's weight (kg) or rep count by a signed delta. */
    private val _weightAdjustments = MutableSharedFlow<Double>(extraBufferCapacity = 4)
    val weightAdjustments: SharedFlow<Double> = _weightAdjustments.asSharedFlow()

    private val _repAdjustments = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val repAdjustments: SharedFlow<Int> = _repAdjustments.asSharedFlow()

    /** Latest on-wrist heart rate streamed live from the watch's sensor during a session, in BPM. */
    private val _watchHeartRate = MutableStateFlow<Int?>(null)
    val watchHeartRate: StateFlow<Int?> = _watchHeartRate.asStateFlow()

    private val _saveWorkoutRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveWorkoutRequested: SharedFlow<Unit> = _saveWorkoutRequested.asSharedFlow()

    private val _discardWorkoutRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val discardWorkoutRequested: SharedFlow<Unit> = _discardWorkoutRequested.asSharedFlow()

    private var listener: MessageClient.OnMessageReceivedListener? = null

    fun init(context: Context) {
        if (listener != null) return
        val l = MessageClient.OnMessageReceivedListener { event ->
            when (event.path) {
                WearSync.PATH_START_WORKOUT -> _startWorkoutRequested.value = true
                WearSync.PATH_TOGGLE_PAUSE -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.TOGGLE_PAUSE)
                WearSync.PATH_MARK_SET_DONE -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.MARK_SET_DONE)
                WearSync.PATH_FINISH_WORKOUT -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.FINISH_WORKOUT)
                WearSync.PATH_ADD_SET -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.ADD_SET)
                WearSync.PATH_ADD_REST -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.ADD_REST)
                WearSync.PATH_SKIP_REST -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.SKIP_REST)
                WearSync.PATH_SELECT_EXERCISE -> WearSync.decodeIndex(event.data)?.let { _selectExerciseRequests.tryEmit(it) }
                WearSync.PATH_ADJUST_WEIGHT -> WearSync.decodeWeightDelta(event.data)?.let { _weightAdjustments.tryEmit(it) }
                WearSync.PATH_ADJUST_REPS -> WearSync.decodeRepsDelta(event.data)?.let { _repAdjustments.tryEmit(it) }
                WearSync.PATH_HEART_RATE -> WearSync.decodeHeartRate(event.data)?.let { _watchHeartRate.value = it }
                WearSync.PATH_REQUEST_WORKOUT_DETAIL -> WearSync.decodeId(event.data)?.let { id ->
                    WorkoutRepository.workouts.find { it.id == id }?.let { pushWorkoutDetail(context.applicationContext, it) }
                }
                WearSync.PATH_SAVE_WORKOUT -> _saveWorkoutRequested.tryEmit(Unit)
                WearSync.PATH_DISCARD_WORKOUT -> _discardWorkoutRequested.tryEmit(Unit)
            }
        }
        listener = l
        Wearable.getMessageClient(context.applicationContext).addListener(l)
    }

    fun consumeStartWorkoutRequest() {
        _startWorkoutRequested.value = false
    }

    /** Reset between sessions so a stale on-wrist reading doesn't linger into the next one. */
    fun clearWatchHeartRate() {
        _watchHeartRate.value = null
    }

    /** Push the latest dashboard snapshot to any listening watch. */
    fun pushTodayStats(context: Context, stats: TodayStats) {
        val request = PutDataRequest.create(WearSync.PATH_TODAY_STATS)
            .setData(WearSync.encodeTodayStats(stats).toByteArray(Charsets.UTF_8))
            .setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { Log.e(TAG, "pushTodayStats: failed to sync", it) }
    }

    /** Push a fresh snapshot of the in-progress session to the watch. */
    fun pushActiveWorkout(context: Context, snapshot: WearSync.ActiveWorkoutSnapshot) {
        val request = PutDataRequest.create(WearSync.PATH_ACTIVE_WORKOUT)
            .setData(WearSync.encodeActiveWorkout(snapshot).toByteArray(Charsets.UTF_8))
            .setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { Log.e(TAG, "pushActiveWorkout: failed to sync", it) }
    }

    /** One-shot push of the just-finished session's recap, shown briefly on the watch before it returns to idle. */
    fun pushWorkoutSummary(context: Context, summary: WearSync.WorkoutSummary) {
        val request = PutDataRequest.create(WearSync.PATH_WORKOUT_SUMMARY)
            .setData(WearSync.encodeWorkoutSummary(summary).toByteArray(Charsets.UTF_8))
            .setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { Log.e(TAG, "pushWorkoutSummary: failed to sync", it) }
    }

    /** Mirror the saved-workout list to the watch's history browser, lightweight (no per-set detail). */
    fun pushWorkoutHistory(context: Context, workouts: List<SavedWorkout>) {
        val entries = workouts.map {
            WearSync.WorkoutHistoryEntry(
                id = it.id,
                title = it.title,
                dateMillis = it.dateMillis,
                durationSec = it.durationSec,
                totalVolumeKg = it.totalVolumeKg,
                totalSets = it.totalSets,
                muscleGroups = it.muscleGroups,
            )
        }
        val request = PutDataRequest.create(WearSync.PATH_WORKOUT_HISTORY)
            .setData(WearSync.encodeWorkoutHistory(entries).toByteArray(Charsets.UTF_8))
            .setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { Log.e(TAG, "pushWorkoutHistory: failed to sync", it) }
    }

    /** Reply to the watch's [WearSync.PATH_REQUEST_WORKOUT_DETAIL] with that workout's full exercise/set breakdown. */
    private fun pushWorkoutDetail(context: Context, workout: SavedWorkout) {
        val request = PutDataRequest.create(WearSync.PATH_WORKOUT_DETAIL)
            .setData(WearSync.encodeSavedWorkout(workout).toByteArray(Charsets.UTF_8))
            .setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { Log.e(TAG, "pushWorkoutDetail: failed to sync", it) }
    }

    /** Remove the active-workout snapshot once the session ends, so the watch goes back to idle. */
    fun clearActiveWorkout(context: Context) {
        val uri = PutDataRequest.create(WearSync.PATH_ACTIVE_WORKOUT).uri
        Wearable.getDataClient(context.applicationContext).deleteDataItems(uri)
            .addOnFailureListener { Log.e(TAG, "clearActiveWorkout: failed", it) }
    }

    /** Remove the one-shot summary so it doesn't replay on watch app restarts after the session is dismissed. */
    fun clearWorkoutSummary(context: Context) {
        val uri = PutDataRequest.create(WearSync.PATH_WORKOUT_SUMMARY).uri
        Wearable.getDataClient(context.applicationContext).deleteDataItems(uri)
            .addOnFailureListener { Log.e(TAG, "clearWorkoutSummary: failed", it) }
    }
}
