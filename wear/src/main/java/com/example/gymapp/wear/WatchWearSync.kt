package com.example.gymapp.wear

import android.content.Context
import android.util.Log
import com.example.gymapp.TodayStats
import com.example.gymapp.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Watch side of the Wearable Data Layer bridge to the phone app. Mirrors
 * [TodayStats] pushed by [com.example.gymapp.PhoneWearSync] into [todayStats],
 * and forwards "start workout" taps back to the phone as a plain message.
 */
object WatchWearSync {

    private const val TAG = "WatchWearSync"

    private val _todayStats = MutableStateFlow<TodayStats?>(null)
    val todayStats: StateFlow<TodayStats?> = _todayStats.asStateFlow()

    private val _activeWorkout = MutableStateFlow<WearSync.ActiveWorkoutSnapshot?>(null)
    val activeWorkout: StateFlow<WearSync.ActiveWorkoutSnapshot?> = _activeWorkout.asStateFlow()

    private var listener: DataClient.OnDataChangedListener? = null

    fun init(context: Context) {
        if (listener != null) return
        val l = DataClient.OnDataChangedListener { events ->
            events.forEach { event -> applyDataEvent(event) }
        }
        listener = l
        val client = Wearable.getDataClient(context.applicationContext)
        client.addListener(l)

        // Seed from whatever's already synced — the phone may have pushed
        // data before this listener was registered, and OnDataChangedListener
        // only fires for changes that happen *after* registration.
        client.dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item -> applyDataItem(item.uri.path, item.data) }
            buffer.release()
        }.addOnFailureListener { Log.e(TAG, "startup snapshot: failed to fetch data items", it) }
    }

    private fun applyDataEvent(event: DataEvent) {
        when (event.dataItem.uri.path) {
            WearSync.PATH_TODAY_STATS, WearSync.PATH_ACTIVE_WORKOUT -> {
                if (event.type == DataEvent.TYPE_DELETED) {
                    if (event.dataItem.uri.path == WearSync.PATH_ACTIVE_WORKOUT) _activeWorkout.value = null
                } else {
                    applyDataItem(event.dataItem.uri.path, event.dataItem.data)
                }
            }
        }
    }

    private fun applyDataItem(path: String?, bytes: ByteArray?) {
        if (bytes == null) return
        val json = String(bytes, Charsets.UTF_8)
        when (path) {
            WearSync.PATH_TODAY_STATS -> WearSync.decodeTodayStats(json)?.let { _todayStats.value = it }
            WearSync.PATH_ACTIVE_WORKOUT -> WearSync.decodeActiveWorkout(json)?.let { _activeWorkout.value = it }
        }
    }

    /** Ask the phone to start an empty workout session. */
    suspend fun sendStartWorkout(context: Context) = sendCommand(context, WearSync.PATH_START_WORKOUT)

    /** Pause/resume the running timer. */
    suspend fun sendTogglePause(context: Context) = sendCommand(context, WearSync.PATH_TOGGLE_PAUSE)

    /** Mark the current set done and kick off the rest timer. */
    suspend fun sendMarkSetDone(context: Context) = sendCommand(context, WearSync.PATH_MARK_SET_DONE)

    /** End the session and jump to the summary screen. */
    suspend fun sendFinishWorkout(context: Context) = sendCommand(context, WearSync.PATH_FINISH_WORKOUT)

    /** Relay a live on-wrist heart rate reading (from [WatchHeartRateMonitor]) to the phone. */
    suspend fun sendHeartRate(context: Context, bpm: Int) =
        sendMessage(context, WearSync.PATH_HEART_RATE, WearSync.encodeHeartRate(bpm))

    private suspend fun sendCommand(context: Context, path: String) = sendMessage(context, path, ByteArray(0))

    private suspend fun sendMessage(context: Context, path: String, payload: ByteArray) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val nodes = runCatching { Tasks.await(Wearable.getNodeClient(ctx).connectedNodes) }.getOrDefault(emptyList())
        nodes.forEach { node ->
            runCatching {
                Tasks.await(Wearable.getMessageClient(ctx).sendMessage(node.id, path, payload))
            }.onFailure { Log.e(TAG, "sendMessage($path): failed to send to ${node.displayName}", it) }
        }
    }
}
