package com.example.gymapp

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phone side of the Wearable Data Layer bridge to the watch companion.
 * Receives "start workout" requests from the watch (mirrored into
 * [startWorkoutRequested], the same pattern as [WorkoutTimer.finishRequested])
 * and pushes [TodayStats] snapshots out to the watch whenever the dashboard
 * refreshes.
 */
object PhoneWearSync {

    private const val TAG = "PhoneWearSync"

    private val _startWorkoutRequested = MutableStateFlow(false)
    val startWorkoutRequested: StateFlow<Boolean> = _startWorkoutRequested.asStateFlow()

    private var listener: MessageClient.OnMessageReceivedListener? = null

    fun init(context: Context) {
        if (listener != null) return
        val l = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WearSync.PATH_START_WORKOUT) {
                _startWorkoutRequested.value = true
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
}
