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
enum class ActiveWorkoutCommand { TOGGLE_PAUSE, MARK_SET_DONE, FINISH_WORKOUT }

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

    private var listener: MessageClient.OnMessageReceivedListener? = null

    fun init(context: Context) {
        if (listener != null) return
        val l = MessageClient.OnMessageReceivedListener { event ->
            when (event.path) {
                WearSync.PATH_START_WORKOUT -> _startWorkoutRequested.value = true
                WearSync.PATH_TOGGLE_PAUSE -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.TOGGLE_PAUSE)
                WearSync.PATH_MARK_SET_DONE -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.MARK_SET_DONE)
                WearSync.PATH_FINISH_WORKOUT -> _activeWorkoutCommands.tryEmit(ActiveWorkoutCommand.FINISH_WORKOUT)
            }
        }
        listener = l
        Wearable.getMessageClient(context.applicationContext).addListener(l)
    }

    fun consumeStartWorkoutRequest() {
        _startWorkoutRequested.value = false
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

    /** Remove the active-workout snapshot once the session ends, so the watch goes back to idle. */
    fun clearActiveWorkout(context: Context) {
        val uri = PutDataRequest.create(WearSync.PATH_ACTIVE_WORKOUT).uri
        Wearable.getDataClient(context.applicationContext).deleteDataItems(uri)
            .addOnFailureListener { Log.e(TAG, "clearActiveWorkout: failed", it) }
    }
}
