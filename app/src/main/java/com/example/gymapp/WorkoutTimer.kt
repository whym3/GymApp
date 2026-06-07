package com.example.gymapp

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

    val elapsed: Int get() = _state.value.elapsedSec

    fun start() {
        _finishRequested.value = false
        _state.value = State(active = true, running = true, elapsedSec = 0)
        startTicking()
    }

    fun pause() {
        if (!_state.value.running) return
        _state.update { it.copy(running = false) }
        stopTicking()
    }

    fun resume() {
        if (!_state.value.active || _state.value.running) return
        _state.update { it.copy(running = true) }
        startTicking()
    }

    fun toggle() {
        if (_state.value.running) pause() else resume()
    }

    fun stop() {
        stopTicking()
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
                delay(1000L)
                _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }
}
