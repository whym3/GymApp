package com.example.gymapp

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single source of truth for the running-workout timer. Both the Compose UI and
 * the foreground [WorkoutService] (notification controls) read/drive this, so the
 * elapsed time and play/pause state stay in sync wherever the user changes them.
 *
 * Elapsed time is derived from [SystemClock.elapsedRealtime] anchors rather than
 * counted in 1-second sleeps, so it can't drift over a long session and stays
 * correct across scheduling hiccups; the ticker only refreshes the published
 * whole-second value.
 */
object WorkoutTimer {

    data class State(
        val active: Boolean = false,
        val running: Boolean = false,
        val elapsedSec: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Set true when the user taps "Finish" from the notification. */
    private val _finishRequested = MutableStateFlow(false)
    val finishRequested: StateFlow<Boolean> = _finishRequested.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    // Millis accumulated across completed run stretches, plus the monotonic
    // timestamp the current stretch (if running) started at.
    private var accumulatedMs = 0L
    private var runStartedAt = 0L

    val elapsed: Int get() = _state.value.elapsedSec

    private fun currentElapsedMs(): Long =
        accumulatedMs + if (_state.value.running) SystemClock.elapsedRealtime() - runStartedAt else 0L

    fun start() {
        _finishRequested.value = false
        accumulatedMs = 0L
        runStartedAt = SystemClock.elapsedRealtime()
        _state.value = State(active = true, running = true, elapsedSec = 0)
        startTicking()
    }

    /** Re-arm a session recovered from the journal after process death. */
    fun restore(elapsedSec: Int, running: Boolean) {
        _finishRequested.value = false
        accumulatedMs = elapsedSec * 1000L
        runStartedAt = SystemClock.elapsedRealtime()
        _state.value = State(active = true, running = running, elapsedSec = elapsedSec)
        if (running) startTicking()
    }

    fun pause() {
        if (!_state.value.running) return
        accumulatedMs = currentElapsedMs()
        _state.update { it.copy(running = false) }
        stopTicking()
    }

    fun resume() {
        if (!_state.value.active || _state.value.running) return
        runStartedAt = SystemClock.elapsedRealtime()
        _state.update { it.copy(running = true) }
        startTicking()
    }

    fun toggle() {
        if (_state.value.running) pause() else resume()
    }

    fun stop() {
        stopTicking()
        accumulatedMs = 0L
        _state.value = State()
        _finishRequested.value = false
    }

    fun requestFinish() {
        _finishRequested.value = true
        pause()
    }

    fun consumeFinish() {
        _finishRequested.value = false
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val sec = (currentElapsedMs() / 1000L).toInt()
                if (sec != _state.value.elapsedSec) _state.update { it.copy(elapsedSec = sec) }
                delay(250L)
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }
}
