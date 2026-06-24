package com.snuabar.counter.ui.screen.counting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.DetectionConfig
import com.snuabar.counter.core.detection.SensorType
import com.snuabar.counter.core.detection.vision.VisionDetectionEngine
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountingViewModel @Inject constructor(
    private val visionDetectionEngine: VisionDetectionEngine,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            visionDetectionEngine.countEvents.collect { event ->
                _currentCount.value = event.count
                _confidence.value = event.confidence
            }
        }
        viewModelScope.launch {
            userRepository.ensureDefaultUser()
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    _currentUser.value = userRepository.getUser(it)
                }
            }
        }
    }

    fun startCounting() {
        _isRunning.value = true
        visionDetectionEngine.start(
            DetectionConfig(sensorType = SensorType.VISION, threshold = 0.7f)
        )
    }

    fun pauseCounting() {
        _isRunning.value = false
        visionDetectionEngine.pause()
    }

    fun resumeCounting() {
        _isRunning.value = true
        visionDetectionEngine.resume()
    }

    fun stopCounting() {
        _isRunning.value = false
        visionDetectionEngine.stop()
    }

    fun resetCount() {
        _currentCount.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        visionDetectionEngine.stop()
    }
}
