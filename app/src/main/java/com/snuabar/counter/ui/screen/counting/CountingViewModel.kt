package com.snuabar.counter.ui.screen.counting

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.detection.tflite.PoseModelConfig
import com.snuabar.counter.core.detection.tflite.action.DetectionDebugInfo
import com.snuabar.counter.domain.model.ActionType
import com.snuabar.counter.core.service.TimerService
import com.snuabar.counter.core.service.TimerStateHolder
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.data.local.prefs.DetectionPreferences
import com.snuabar.counter.domain.model.CountingSession
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.CountingSessionRepository
import com.snuabar.counter.domain.repository.TemplateRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class CountingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detectionEngineFactory: DetectionEngineFactory,
    private val userRepository: UserRepository,
    private val sessionRepository: CountingSessionRepository,
    private val detectionPreferences: DetectionPreferences,
    private val templateRepository: TemplateRepository,
    private val recordingSession: RecordingSession,
    private val timerStateHolder: TimerStateHolder
) : ViewModel() {

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    // Keypoints for visualization (from TFLite engine)
    private val _keypoints = MutableStateFlow<Array<FloatArray>?>(null)
    val keypoints: StateFlow<Array<FloatArray>?> = _keypoints.asStateFlow()

    // FPS from TFLite engine
    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

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

    // Observe timer service state
    private var timerObserverJob: Job? = null

    private val _targetSeconds = MutableStateFlow<Int?>(null)
    val targetSeconds: StateFlow<Int?> = _targetSeconds.asStateFlow()

    private val _targetResolution = MutableStateFlow(android.util.Size(640, 360))
    val targetResolution: StateFlow<android.util.Size> = _targetResolution.asStateFlow()

    private val _actionType = MutableStateFlow(ActionType.CUSTOM)
    val actionType: StateFlow<ActionType> = _actionType.asStateFlow()

    // Template selection
    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<Template?>(null)
    val selectedTemplate: StateFlow<Template?> = _selectedTemplate.asStateFlow()

    // Target settings
    private val _targetCount = MutableStateFlow<Int?>(null)
    val targetCount: StateFlow<Int?> = _targetCount.asStateFlow()

    // Debug info from action detector
    private val _debugInfo = MutableStateFlow<DetectionDebugInfo?>(null)
    val debugInfo: StateFlow<DetectionDebugInfo?> = _debugInfo.asStateFlow()
    private val _sessionMode = MutableStateFlow(SessionMode.COUNTING)
    val sessionMode: StateFlow<SessionMode> = _sessionMode.asStateFlow()

    // Camera lens: false = back, true = front
    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // Available camera info: id to display name
    private val _availableCameras = MutableStateFlow<Map<String, String>>(emptyMap())
    val availableCameras: StateFlow<Map<String, String>> = _availableCameras.asStateFlow()

    // Selected camera ID (null means use default based on isFrontCamera)
    private val _selectedCameraId = MutableStateFlow<String?>(null)
    val selectedCameraId: StateFlow<String?> = _selectedCameraId.asStateFlow()

    // Store camera lens facing info for proper CameraSelector building
    private var cameraLensFacing = mutableMapOf<String, Int>()

    init {
        // Load templates and auto-select the first one
        viewModelScope.launch {
            templateRepository.getAllTemplates().collect { templateList ->
                // Filter out templates without feature vectors (not yet recorded)
                val recordedTemplates = templateList.filter {
                    it.featureVector != null && it.featureVector.isNotEmpty()
                }
                android.util.Log.d("CountingViewModel", "Loaded ${templateList.size} templates, ${recordedTemplates.size} have feature vectors")
                _templates.value = recordedTemplates
                // Auto-select first template if none selected
                if (recordedTemplates.isNotEmpty() && _selectedTemplate.value == null) {
                    selectTemplate(recordedTemplates.first())
                }
            }
        }

        // Enumerate available cameras with meaningful names
        viewModelScope.launch {
            try {
                val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                Log.d("CameraDebug", "=== Camera2 cameraIdList: ${cameraManager.cameraIdList.toList()} ===")
                
                val cameraInfo = mutableMapOf<String, String>()
                // Track names to add suffix for duplicates
                val usedNames = mutableMapOf<String, Int>()
                
                fun addCamera(cameraId: String, characteristics: CameraCharacteristics, isPhysical: Boolean = false) {
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    
                    Log.d("CameraDebug", "Camera[$cameraId]: facing=$lensFacing, focal=${focalLengths?.toList()}, sensor=$sensorSize, isPhysical=$isPhysical")
                    
                    // Store lens facing for later use
                    if (lensFacing != null) {
                        cameraLensFacing[cameraId] = lensFacing
                    }
                    
                    val facingStr = when (lensFacing) {
                        CameraCharacteristics.LENS_FACING_BACK -> "后置"
                        CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                        else -> "未知"
                    }
                    
                    val cameraType = if (focalLengths != null && focalLengths.isNotEmpty() && sensorSize != null) {
                        val focalLength = focalLengths[0]
                        val equivalentFocal = focalLength * (36.0f / sensorSize.width)
                        when {
                            equivalentFocal < 20f -> "超广角"
                            equivalentFocal < 35f -> "广角"
                            equivalentFocal < 70f -> "标准"
                            equivalentFocal < 120f -> "中长焦"
                            else -> "长焦"
                        }
                    } else {
                        ""
                    }
                    
                    val baseName = if (cameraType.isNotEmpty()) {
                        "$facingStr $cameraType"
                    } else {
                        "$facingStr 摄像头"
                    }
                    
                    // Add suffix if duplicate name exists
                    val count = usedNames.getOrDefault(baseName, 0)
                    usedNames[baseName] = count + 1
                    val displayName = if (count > 0) "$baseName ${count + 1} [$cameraId]" else "$baseName [$cameraId]"
                    
                    cameraInfo[cameraId] = displayName
                }
                
                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    // LOGICAL_MULTI_CAMERA_PHYSICAL_IDS key
                    val physicalIdsKey = android.hardware.camera2.CameraCharacteristics.Key("android.logicalMultiCamera.physicalIds", ByteArray::class.java)
                    val physicalIds: ByteArray? = try { characteristics.get(physicalIdsKey) } catch (e: Exception) { null }
                    
                    // Add the logical camera itself
                    addCamera(cameraId, characteristics)
                    
                    // Also enumerate physical cameras behind logical multi-camera
                    if (physicalIds != null && physicalIds.isNotEmpty()) {
                        val idString = String(physicalIds, Charsets.US_ASCII)
                        val physicalIdList = idString.split('\u0000').filter { s -> s.isNotEmpty() }
                        Log.d("CameraDebug", "Camera[$cameraId] has physical cameras: $physicalIdList")
                        for (physId in physicalIdList) {
                            try {
                                val physChars = cameraManager.getCameraCharacteristics(physId)
                                addCamera(physId, physChars, isPhysical = true)
                            } catch (e: Exception) {
                                Log.d("CameraDebug", "Cannot access physical camera $physId: ${e.message}")
                            }
                        }
                    }
                }
                
                Log.d("CameraDebug", "=== Final camera list: $cameraInfo ===")
                _availableCameras.value = cameraInfo
            } catch (e: Exception) {
                Log.e("CameraDebug", "Camera enumeration failed", e)
            }
        }
    }

    fun toggleCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        _selectedCameraId.value = null // Reset to default selection
        currentEngine?.setCameraInfo(_isFrontCamera.value)
    }

    fun selectCamera(cameraId: String?) {
        _selectedCameraId.value = cameraId
        // Update isFrontCamera based on selected camera
        if (cameraId != null) {
            try {
                val cameraManager = appContext.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val isFront = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                _isFrontCamera.value = isFront
                currentEngine?.setCameraInfo(isFront)
            } catch (e: Exception) {
                Log.e("CameraDebug", "Failed to get camera characteristics for $cameraId", e)
            }
        }
    }

    fun setActionType(actionType: ActionType) {
        _actionType.value = actionType
    }

    fun selectTemplate(template: Template) {
        _selectedTemplate.value = template
        // Use template's actionType if available, otherwise CUSTOM
        _actionType.value = template.actionType ?: ActionType.CUSTOM
        _sessionMode.value = template.mode
        _targetSeconds.value = template.targetSeconds
    }

    fun setTargetCount(count: Int?) {
        _targetCount.value = count
    }

    fun setTargetSeconds(seconds: Int?) {
        _targetSeconds.value = seconds
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

    // Cached detection preferences
    private var cachedThreshold: Float = 0.7f
    private var cachedPoseModelConfig: PoseModelConfig = PoseModelConfig.STANDARD

    init {
        viewModelScope.launch {
            userRepository.ensureDefaultUser()
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    _currentUser.value = userRepository.getUser(it)
                }
            }
        }

        // Pre-create TFLite engine for pose-based detection
        currentEngine = detectionEngineFactory.create(SensorType.VISION, EngineType.TFLITE)
        currentSensorType = SensorType.VISION
        viewModelScope.launch {
            currentEngine?.countEvents?.collect { event ->
                _currentCount.value = event.count
                _confidence.value = event.confidence
                event.debugInfo?.let { _debugInfo.value = it }
            }
        }

        // Cache threshold and pose model config from preferences
        viewModelScope.launch {
            detectionPreferences.thresholdFlow.collect { value ->
                cachedThreshold = value
            }
        }
        viewModelScope.launch {
            detectionPreferences.poseModelConfigFlow.collect { value ->
                val changed = cachedPoseModelConfig != value
                cachedPoseModelConfig = value
                // Reload model if engine is already running
                if (changed) {
                    reloadModelIfNeeded()
                }
            }
        }
    }

    /**
     * Process a bitmap frame directly (for Camera2 integration).
     */
    fun processBitmap(bitmap: android.graphics.Bitmap) {
        val engine = currentEngine
        if (engine is com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine) {
            engine.processBitmap(bitmap)
        }
    }

    private var keypointCollectionJob: Job? = null

    /**
     * Setup detection engine when camera preview starts.
     * Loads model and runs inference for skeleton overlay, but doesn't start counting.
     */
    fun setupDetection() {
        val engine = currentEngine ?: return
        if (engine.isRunning()) {
            // Already running, just ensure keypoints collection is active
            ensureKeypointsCollection()
            return
        }
        // Start engine for pose detection only (inference + skeleton, no counting)
        if (engine is com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine) {
            engine.setCameraInfo(_isFrontCamera.value)
            val config = DetectionConfig(
                sensorType = SensorType.VISION,
                poseModelConfig = cachedPoseModelConfig,
                threshold = cachedThreshold,
                actionType = _actionType.value
            )
            engine.startPreview(config)
            ensureKeypointsCollection()
        }
    }

    /**
     * Ensure keypoints collection coroutine is running.
     */
    private fun ensureKeypointsCollection() {
        val tfliteEngine = currentEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine ?: return
        keypointCollectionJob?.cancel()
        keypointCollectionJob = viewModelScope.launch {
            launch {
                tfliteEngine.keypoints.collect { kps ->
                    _keypoints.value = kps
                }
            }
            launch {
                tfliteEngine.fps.collect { fps ->
                    _fps.value = fps
                }
            }
        }
    }

    /**
     * Stop detection engine (called when camera preview closes).
     */
    fun stopDetection() {
        currentEngine?.stop()
        keypointCollectionJob?.cancel()
        _keypoints.value = null
    }

    /**
     * Reload the TFLite model when pose model config changes.
     */
    private fun reloadModelIfNeeded() {
        val engine = currentEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine ?: return
        if (!engine.isRunning()) return
        // Stop and restart with new model
        engine.stop()
        _keypoints.value = null
        val config = DetectionConfig(
            sensorType = SensorType.VISION,
            poseModelConfig = cachedPoseModelConfig,
            threshold = cachedThreshold,
            actionType = _actionType.value
        )
        engine.startPreview(config)
        ensureKeypointsCollection()
    }

    private fun setupEngine(sensorType: SensorType) {
        val needsNewEngine = currentEngine == null || currentSensorType != sensorType || currentEngine !is com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
        if (needsNewEngine) {
            currentEngine?.stop()
            keypointCollectionJob?.cancel()
            currentEngine = detectionEngineFactory.create(sensorType, EngineType.TFLITE)
            currentSensorType = sensorType

            viewModelScope.launch {
                currentEngine?.countEvents?.collect { event ->
                    _currentCount.value = event.count
                    _confidence.value = event.confidence
                    event.debugInfo?.let { _debugInfo.value = it }
                }
            }
        }

        // Always ensure keypoints collection is running for TFLite
        val tfliteEngine = currentEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
        tfliteEngine?.let { engine ->
            keypointCollectionJob?.cancel()
            keypointCollectionJob = viewModelScope.launch {
                launch {
                    engine.keypoints.collect { kps ->
                        _keypoints.value = kps
                    }
                }
                launch {
                    engine.fps.collect { fps ->
                        _fps.value = fps
                    }
                }
            }
        }
    }

    fun startCounting(
        sensorType: SensorType = SensorType.VISION,
        mode: SessionMode? = null,
        targetSeconds: Int? = null,
        targetResolution: android.util.Size = android.util.Size(640, 360),
        actionType: ActionType = ActionType.CUSTOM,
        templateId: Long? = null
    ) {
        // Use internal state if not explicitly provided
        val actualMode = mode ?: _sessionMode.value
        val actualTemplateId = templateId ?: _selectedTemplate.value?.id
        val actualTargetSeconds = targetSeconds ?: _targetSeconds.value
        val actualSensorType = _selectedTemplate.value?.sensorType ?: sensorType
        val actualActionType = _actionType.value
        
        // Use TFLite for pose-based detection (all visual actions use pose detection)
        setupEngine(actualSensorType)
        _isRunning.value = true
        _isTimerMode.value = (actualMode == SessionMode.TIMER)
        _elapsedSeconds.value = 0
        sessionStartTime = System.currentTimeMillis()
        currentTemplateId = actualTemplateId
        currentMode = actualMode
        _currentCount.value = 0

        // Create running session immediately for realtime save
        viewModelScope.launch {
            val userId = _currentUser.value?.id ?: 1L
            val session = CountingSession(
                userId = userId,
                name = when (actualMode) {
                    SessionMode.TIMER -> "计时训练"
                    else -> "计数训练"
                },
                templateId = currentTemplateId,
                sensorType = actualSensorType,
                startTime = sessionStartTime,
                targetCount = actualTargetSeconds,
                finalCount = 0,
                status = com.snuabar.counter.domain.model.SessionStatus.RUNNING
            )
            currentSessionId = sessionRepository.createSession(session)
        }

        // Start realtime save job
        startRealtimeSave()

        if (actualMode == SessionMode.TIMER) {
            // Timer is handled by TimerService for background support
            viewModelScope.launch {
                val voiceEnabled = detectionPreferences.voiceAnnouncementFlow.first()
                TimerService.startTimer(appContext, actualTargetSeconds, voiceEnabled)
            }
            startTimerService()
        }

        // Resolve template and start engine
        viewModelScope.launch {
            var resolvedTemplate: com.snuabar.counter.domain.model.Template? = null
            if (actualTemplateId != null) {
                resolvedTemplate = templateRepository.getTemplate(actualTemplateId)
                if (resolvedTemplate != null) {
                    val fvSize = resolvedTemplate.featureVector?.size ?: 0
                    android.util.Log.d("CountingViewModel", "Resolved template id=$actualTemplateId, name=${resolvedTemplate.name}, featureVector size=$fvSize")
                    if (fvSize == 0) {
                        android.util.Log.e("CountingViewModel", "WARNING: Template ${resolvedTemplate.name} has empty feature vector!")
                    }
                } else {
                    android.util.Log.e("CountingViewModel", "WARNING: Could not resolve template id=$actualTemplateId")
                }
            }

            currentEngine?.start(
                DetectionConfig(
                    sensorType = actualSensorType,
                    threshold = cachedThreshold,
                    mode = actualMode,
                    targetSeconds = actualTargetSeconds,
                    targetResolution = targetResolution,
                    poseModelConfig = cachedPoseModelConfig,
                    actionType = actualActionType,
                    template = resolvedTemplate
                )
            )
        }
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

    private fun startTimerService() {
        // Start observing TimerStateHolder for elapsed time updates
        timerObserverJob?.cancel()
        timerObserverJob = viewModelScope.launch {
            timerStateHolder.elapsedSeconds.collect { elapsed ->
                _elapsedSeconds.value = elapsed
            }
        }
        // Reset state
        timerStateHolder.updateElapsed(0)
        timerStateHolder.setServiceRunning(true)
    }

    fun pauseCounting() {
        _isRunning.value = false
        if (_isTimerMode.value) {
            TimerService.pauseTimer(appContext)
            timerStateHolder.setServiceRunning(false)
        } else {
            currentEngine?.pause()
        }
    }

    fun resumeCounting() {
        _isRunning.value = true
        if (_isTimerMode.value) {
            TimerService.resumeTimer(appContext)
            timerStateHolder.setServiceRunning(true)
        } else {
            currentEngine?.resume()
        }
    }

    fun stopCounting() {
        _isRunning.value = false
        timerJob?.cancel()
        realtimeSaveJob?.cancel()
        timerObserverJob?.cancel()
        // Stop counting but keep engine running for skeleton display
        val engine = currentEngine
        if (engine is com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine) {
            engine.stopCounting()
        }
        recordingSession.setKeypointCallback(null)

        // Stop timer service if in timer mode
        if (_isTimerMode.value) {
            TimerService.stopTimer(appContext)
            timerStateHolder.setServiceRunning(false)
        }

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
        timerObserverJob?.cancel()
        currentEngine?.stop()
        if (_isTimerMode.value) {
            TimerService.stopTimer(appContext)
            timerStateHolder.setServiceRunning(false)
        }
    }
}
