package com.example.gymapp.wear

import android.content.Context
import android.util.Log
import com.example.gymapp.SavedWorkout
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

    /** One-shot recap pushed the moment a session finishes — shown briefly, then [consumeWorkoutSummary] clears it. */
    private val _workoutSummary = MutableStateFlow<WearSync.WorkoutSummary?>(null)
    val workoutSummary: StateFlow<WearSync.WorkoutSummary?> = _workoutSummary.asStateFlow()

    /** Lightweight saved-workout list for the watch's history browser, mirrored from the phone. */
    private val _workoutHistory = MutableStateFlow<List<WearSync.WorkoutHistoryEntry>>(emptyList())
    val workoutHistory: StateFlow<List<WearSync.WorkoutHistoryEntry>> = _workoutHistory.asStateFlow()

    /** Full detail for whichever history entry was last tapped, fetched on demand via [sendRequestWorkoutDetail]. */
    private val _workoutDetail = MutableStateFlow<SavedWorkout?>(null)
    val workoutDetail: StateFlow<SavedWorkout?> = _workoutDetail.asStateFlow()

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
        // PATH_WORKOUT_SUMMARY is intentionally excluded from the seed: it's a
        // one-shot "good job" notification and must not replay on cold restarts.
        // The phone deletes it via clearWorkoutSummary() once the session ends.
        client.dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item ->
                if (item.uri.path != WearSync.PATH_WORKOUT_SUMMARY) {
                    applyDataItem(item.uri.path, item.data)
                }
            }
            buffer.release()
        }.addOnFailureListener { Log.e(TAG, "startup snapshot: failed to fetch data items", it) }
    }

    private fun applyDataEvent(event: DataEvent) {
        when (event.dataItem.uri.path) {
            WearSync.PATH_TODAY_STATS, WearSync.PATH_ACTIVE_WORKOUT, WearSync.PATH_WORKOUT_SUMMARY,
            WearSync.PATH_WORKOUT_HISTORY, WearSync.PATH_WORKOUT_DETAIL,
            -> {
                if (event.type == DataEvent.TYPE_DELETED) {
                    when (event.dataItem.uri.path) {
                        WearSync.PATH_ACTIVE_WORKOUT -> _activeWorkout.value = null
                        // Phone saved/discarded from its own summary screen —
                        // dismiss ours too instead of sitting on Save/Discard.
                        WearSync.PATH_WORKOUT_SUMMARY -> _workoutSummary.value = null
                    }
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
            WearSync.PATH_WORKOUT_SUMMARY -> WearSync.decodeWorkoutSummary(json)?.let { _workoutSummary.value = it }
            WearSync.PATH_WORKOUT_HISTORY -> _workoutHistory.value = WearSync.decodeWorkoutHistory(json)
            WearSync.PATH_WORKOUT_DETAIL -> WearSync.decodeSavedWorkout(json)?.let { _workoutDetail.value = it }
        }
    }

    /** Dismiss the recap once the wearer has glanced at it (or it's timed out). */
    fun consumeWorkoutSummary() {
        _workoutSummary.value = null
    }

    /** Ask the phone for one saved workout's full exercise/set breakdown, by id. */
    suspend fun sendRequestWorkoutDetail(context: Context, id: Long) =
        sendMessage(context, WearSync.PATH_REQUEST_WORKOUT_DETAIL, WearSync.encodeId(id))

    /** Drop the last-fetched detail once the wearer navigates away, so a stale entry doesn't flash on the next open. */
    fun consumeWorkoutDetail() {
        _workoutDetail.value = null
    }

    /** Ask the phone to start an empty workout session. */
    suspend fun sendStartWorkout(context: Context) = sendCommand(context, WearSync.PATH_START_WORKOUT)

    /** Pause/resume the running timer. */
    suspend fun sendTogglePause(context: Context) = sendCommand(context, WearSync.PATH_TOGGLE_PAUSE)

    /** Mark the current set done and kick off the rest timer. */
    suspend fun sendMarkSetDone(context: Context) = sendCommand(context, WearSync.PATH_MARK_SET_DONE)

    /** End the session and jump to the summary screen. */
    suspend fun sendFinishWorkout(context: Context) = sendCommand(context, WearSync.PATH_FINISH_WORKOUT)

    /** Save the just-finished session. */
    suspend fun sendSaveWorkout(context: Context) = sendCommand(context, WearSync.PATH_SAVE_WORKOUT)

    /** Discard the just-finished session without saving. */
    suspend fun sendDiscardWorkout(context: Context) = sendCommand(context, WearSync.PATH_DISCARD_WORKOUT)

    /** Make the given exercise (zero-based index into [WearSync.ActiveWorkoutSnapshot.exercises]) the remote's target. */
    suspend fun sendSelectExercise(context: Context, index: Int) =
        sendMessage(context, WearSync.PATH_SELECT_EXERCISE, WearSync.encodeIndex(index))

    /** Nudge the current set's weight by a signed delta, in kg. */
    suspend fun sendAdjustWeight(context: Context, deltaKg: Double) =
        sendMessage(context, WearSync.PATH_ADJUST_WEIGHT, WearSync.encodeDelta(deltaKg))

    /** Nudge the current set's rep count by a signed delta. */
    suspend fun sendAdjustReps(context: Context, delta: Int) =
        sendMessage(context, WearSync.PATH_ADJUST_REPS, WearSync.encodeDelta(delta))

    /** Append another set to the current exercise. */
    suspend fun sendAddSet(context: Context) = sendCommand(context, WearSync.PATH_ADD_SET)

    /** Extend the running rest timer by the phone's usual bump. */
    suspend fun sendAddRest(context: Context) = sendCommand(context, WearSync.PATH_ADD_REST)

    /** Cancel the running rest timer early. */
    suspend fun sendSkipRest(context: Context) = sendCommand(context, WearSync.PATH_SKIP_REST)

    /** Relay a live on-wrist heart rate reading (from [WatchHeartRateMonitor]) to the phone. */
    suspend fun sendHeartRate(context: Context, bpm: Int) =
        sendMessage(context, WearSync.PATH_HEART_RATE, WearSync.encodeHeartRate(bpm))

    /** Relay steps + active calories burned so far this workout session (delta since it started) to the phone. */
    suspend fun sendSessionActivity(context: Context, steps: Long, calories: Double) =
        sendMessage(context, WearSync.PATH_SESSION_ACTIVITY, WearSync.encodeActivityStats(WearSync.ActivityStats(steps, calories)))

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
