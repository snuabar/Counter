package com.snuabar.counter.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared bridge between TimerService and ViewModels.
 * TimerService writes elapsed time here; ViewModels observe it.
 */
@Singleton
class TimerStateHolder @Inject constructor() {

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun updateElapsed(seconds: Int) {
        _elapsedSeconds.value = seconds
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun reset() {
        _elapsedSeconds.value = 0
        _isServiceRunning.value = false
    }
}
