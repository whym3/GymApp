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

    private var listener: DataClient.OnDataChangedListener? = null

    fun init(context: Context) {
        if (listener != null) return
        val l = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearSync.PATH_TODAY_STATS) {
                    val bytes = event.dataItem.data
                    if (bytes != null) {
                        WearSync.decodeTodayStats(String(bytes, Charsets.UTF_8))?.let { _todayStats.value = it }
                    }
                }
            }
        }
        listener = l
        val client = Wearable.getDataClient(context.applicationContext)
        client.addListener(l)

        // Seed from whatever's already synced — the phone may have pushed
        // stats before this listener was registered, and OnDataChangedListener
        // only fires for changes that happen *after* registration.
        client.dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item ->
                if (item.uri.path == WearSync.PATH_TODAY_STATS) {
                    val bytes = item.data
                    if (bytes != null) {
                        WearSync.decodeTodayStats(String(bytes, Charsets.UTF_8))?.let { _todayStats.value = it }
                    }
                }
            }
            buffer.release()
        }.addOnFailureListener { Log.e(TAG, "startup snapshot: failed to fetch data items", it) }
    }

    /** Ask the phone to start an empty workout session. */
    suspend fun sendStartWorkout(context: Context) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val nodes = runCatching { Tasks.await(Wearable.getNodeClient(ctx).connectedNodes) }.getOrDefault(emptyList())
        nodes.forEach { node ->
            runCatching {
                Tasks.await(Wearable.getMessageClient(ctx).sendMessage(node.id, WearSync.PATH_START_WORKOUT, ByteArray(0)))
            }.onFailure { Log.e(TAG, "sendStartWorkout: failed to send to ${node.displayName}", it) }
        }
    }
}
