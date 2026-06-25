package com.snuabar.counter.ui.screen.counting

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.detection.tflite.action.ActionType
import com.snuabar.counter.domain.model.CountingSession
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.CountingSessionRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CountingViewModel @Inject constructor(
    private val detectionEngineFactory: DetectionEngineFactory,
    private val userRepository: UserRepository,
    private val sessionRepository: CountingSessionRepository
) : ViewModel() {

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Timer mode
    private val _isTimerMode = MutableStateFlow(false)
    val isTimerMode: StateFlow<Boolean> = _isTimerMode.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _targetSeconds = MutableStateFlow<Int?>(null)
    val targetSeconds: StateFlow<Int?> = _targetSeconds.asStateFlow()

    private val _targetResolution = MutableStateFlow(android.util.Size(640, 480))
    val targetResolution: StateFlow<android.util.Size> = _targetResolution.asStateFlow()

    private val _actionType = MutableStateFlow(ActionType.PUSH_UP)
    val actionType: StateFlow<ActionType> = _actionType.asStateFlow()

    fun setActionType(actionType: ActionType) {
        _actionType.value = actionType
    }

    fun setTargetResolution(width: Int, height: Int) {
        _targetResolution.value = android.util.Size(width, height)
    }

    private var timerJob: Job? = null

    private var currentEngine: DetectionEngine? = null
    private var currentSensorType: SensorType = SensorType.VISION

    // Session tracking
    private var sessionStartTime: Long = 0L
    private var currentSessionId: Long = 0L
    private var currentTemplateId: Long? = null
    private var currentMode: SessionMode = SessionMode.COUNTING
    private var realtimeSaveJob: Job? = null

    init {
        viewModelScope.launch {
            userRepository.ensureDefaultUser()
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    _currentUser.value = userRepository.getUser(it)
                }
            }
        }

        // Pre-create VISION engine for analyzer access
        currentEngine = detectionEngineFactory.create(SensorType.VISION)
        currentSensorType = SensorType.VISION
        viewModelScope.launch {
            currentEngine?.countEvents?.collect { event ->
                _currentCount.value = event.count
                _confidence.value = event.confidence
            }
        }
    }

    fun getImageAnalyzer(): ImageAnalysis.Analyzer? {
        return currentEngine as? ImageAnalysis.Analyzer
    }

    private fun setupEngine(sensorType: SensorType) {
        if (currentEngine == null || currentSensorType != sensorType) {
            currentEngine?.stop()
            currentEngine = detectionEngineFactory.create(sensorType)
            currentSensorType = sensorType

            viewModelScope.launch {
                currentEngine?.countEvents?.collect { event ->
                    _currentCount.value = event.count
                    _confidence.value = event.confidence
                }
            }
        }
    }

    fun startCounting(
        sensorType: SensorType = SensorType.VISION,
        mode: SessionMode = SessionMode.COUNTING,
        targetSeconds: Int? = null,
        targetResolution: android.util.Size = android.util.Size(640, 480),
        actionType: ActionType = ActionType.PUSH_UP,
        templateId: Long? = null
    ) {
        setupEngine(sensorType)
        _isRunning.value = true
        _isTimerMode.value = (mode == SessionMode.TIMER)
        _targetSeconds.value = targetSeconds
        _elapsedSeconds.value = 0
        sessionStartTime = System.currentTimeMillis()
        currentTemplateId = templateId
        currentMode = mode
        _currentCount.value = 0

        // Create running session immediately for realtime save
        viewModelScope.launch {
            val userId = _currentUser.value?.id ?: 1L
            val session = CountingSession(
                userId = userId,
                name = when (mode) {
                    SessionMode.TIMER -> "计时训练"
                    else -> "计数训练"
                },
                templateId = currentTemplateId,
                sensorType = com.snuabar.counter.domain.model.SensorType.valueOf(sensorType.name),
                startTime = sessionStartTime,
                targetCount = targetSeconds,
                finalCount = 0,
                status = com.snuabar.counter.domain.model.SessionStatus.RUNNING
            )
            currentSessionId = sessionRepository.createSession(session)
        }

        // Start realtime save job
        startRealtimeSave()

        if (mode == SessionMode.TIMER) {
            startTimer()
        }

        currentEngine?.start(
            DetectionConfig(
                sensorType = sensorType,
                threshold = 0.7f,
                mode = mode,
                targetSeconds = targetSeconds,
                targetResolution = targetResolution,
                actionType = actionType
            )
        )
    }

    private fun startRealtimeSave() {
        realtimeSaveJob?.cancel()
        realtimeSaveJob = viewModelScope.launch {
            _currentCount.collect { count ->
                if (currentSessionId > 0 && _isRunning.value) {
                    val session = sessionRepository.getSession(currentSessionId)
                    session?.let {
                        sessionRepository.updateSession(
                            it.copy(finalCount = count)
                        )
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _isRunning.value) {
                delay(1000)
                if (_isRunning.value) {
                    _elapsedSeconds.value += 1
                    // Auto-stop when target reached
                    _targetSeconds.value?.let { target ->
                        if (_elapsedSeconds.value >= target) {
                            stopCounting()
                        }
                    }
                }
            }
        }
    }

    fun pauseCounting() {
        _isRunning.value = false
        currentEngine?.pause()
    }

    fun resumeCounting() {
        _isRunning.value = true
        currentEngine?.resume()
    }

    fun stopCounting() {
        _isRunning.value = false
        timerJob?.cancel()
        realtimeSaveJob?.cancel()
        currentEngine?.stop()

        // Update session to COMPLETED
        if (currentSessionId > 0) {
            viewModelScope.launch {
                val session = sessionRepository.getSession(currentSessionId)
                session?.let {
                    sessionRepository.updateSession(
                        it.copy(
                            endTime = System.currentTimeMillis(),
                            finalCount = _currentCount.value,
                            status = com.snuabar.counter.domain.model.SessionStatus.COMPLETED
                        )
                    )
                }
                currentSessionId = 0L
            }
            sessionStartTime = 0L
        }
    }

    fun resetCount() {
        _currentCount.value = 0
        _elapsedSeconds.value = 0
        sessionStartTime = 0L
        currentSessionId = 0L
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        currentEngine?.stop()
    }
}
