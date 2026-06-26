package com.snuabar.counter.ui.screen.template

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.detection.tflite.PoseModelConfig
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.core.template.TemplateRecorder
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.SessionMode
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.domain.repository.TemplateRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateRepository: TemplateRepository,
    private val userRepository: UserRepository,
    private val recordingSession: RecordingSession,
    private val detectionEngineFactory: DetectionEngineFactory,
    private val detectionPreferences: com.snuabar.counter.data.local.prefs.DetectionPreferences
) : ViewModel() {

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val _showAddTemplateDialog = MutableStateFlow(false)
    val showAddTemplateDialog: StateFlow<Boolean> = _showAddTemplateDialog.asStateFlow()

    private val _newTemplateName = MutableStateFlow("")
    val newTemplateName: StateFlow<String> = _newTemplateName.asStateFlow()

    private val _selectedSensorType = MutableStateFlow(SensorType.VISION)
    val selectedSensorType: StateFlow<SensorType> = _selectedSensorType.asStateFlow()

    private val _selectedSessionMode = MutableStateFlow(SessionMode.COUNTING)
    val selectedSessionMode: StateFlow<SessionMode> = _selectedSessionMode.asStateFlow()

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown: StateFlow<Boolean> = _isCountingDown.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(5)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _recordProgress = MutableStateFlow(0)
    val recordProgress: StateFlow<Int> = _recordProgress.asStateFlow()

    private val _recordTargetFrames = MutableStateFlow(0)
    val recordTargetFrames: StateFlow<Int> = _recordTargetFrames.asStateFlow()

    private val _recordingTemplateName = MutableStateFlow("")
    val recordingTemplateName: StateFlow<String> = _recordingTemplateName.asStateFlow()

    // Keypoints for visualization
    private val _keypoints = MutableStateFlow<Array<FloatArray>?>(null)
    val keypoints: StateFlow<Array<FloatArray>?> = _keypoints.asStateFlow()

    // FPS from TFLite engine
    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    // Detection engine for recording
    private var recordingEngine: DetectionEngine? = null
    // TemplateRecorder instance for active recording
    private var templateRecorder: TemplateRecorder? = null
    private var recordingTemplateId: Long? = null
    private var countdownJob: kotlinx.coroutines.Job? = null

    // Camera state (default front for recording so user can see themselves)
    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // Cached detection preferences
    private var cachedPoseModelConfig: PoseModelConfig = PoseModelConfig.STANDARD

    private val _selectedCameraId = MutableStateFlow<String?>(null)
    val selectedCameraId: StateFlow<String?> = _selectedCameraId.asStateFlow()

    private val _availableCameras = MutableStateFlow<Map<String, String>>(emptyMap())
    val availableCameras: StateFlow<Map<String, String>> = _availableCameras.asStateFlow()

    init {
        viewModelScope.launch {
            templateRepository.getAllTemplates().collect { list ->
                _templates.value = list
            }
        }

        // Cache pose model config from preferences
        viewModelScope.launch {
            detectionPreferences.poseModelConfigFlow.collect { config ->
                cachedPoseModelConfig = config
            }
        }

        // Enumerate available cameras
        viewModelScope.launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraInfo = mutableMapOf<String, String>()
                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val facingStr = when (lensFacing) {
                        CameraCharacteristics.LENS_FACING_BACK -> "后置"
                        CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                        else -> "未知"
                    }
                    cameraInfo[cameraId] = "$facingStr 摄像头 [$cameraId]"
                }
                _availableCameras.value = cameraInfo
            } catch (e: Exception) {
                Log.e("TemplateViewModel", "Camera enumeration failed", e)
            }
        }
    }

    fun createTemplate(name: String, sensorType: SensorType, mode: SessionMode = SessionMode.COUNTING) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first()
            val template = Template(
                userId = userId,
                name = name,
                type = TemplateType.CUSTOM,
                sensorType = sensorType,
                mode = mode
            )
            templateRepository.createTemplate(template)
            _showAddTemplateDialog.value = false
            _newTemplateName.value = ""
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
        }
    }

    fun setShowAddTemplateDialog(show: Boolean) {
        _showAddTemplateDialog.value = show
    }

    fun setNewTemplateName(name: String) {
        _newTemplateName.value = name
    }

    fun setSelectedSensorType(sensorType: SensorType) {
        _selectedSensorType.value = sensorType
    }

    fun setSelectedSessionMode(mode: SessionMode) {
        _selectedSessionMode.value = mode
    }

    // ---- Camera methods ----

    fun toggleCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        _selectedCameraId.value = null
        recordingEngine?.setCameraInfo(_isFrontCamera.value)
    }

    fun selectCamera(cameraId: String?) {
        _selectedCameraId.value = cameraId
        // Update isFrontCamera based on selected camera
        if (cameraId != null) {
            try {
                val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val isFront = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                _isFrontCamera.value = isFront
                recordingEngine?.setCameraInfo(isFront)
            } catch (e: Exception) {
                Log.e("CameraDebug", "Failed to get camera characteristics for $cameraId", e)
            }
        }
    }

    // ---- Recording methods ----

    /**
     * Start recording for an existing template (add feature vector).
     */
    fun startRecordingForTemplate(templateId: Long, templateName: String, durationSeconds: Int = 3) {
        recordingTemplateId = templateId
        startRecordingInternal(templateName, durationSeconds)
    }

    /**
     * Start recording a new template.
     * @param templateName name for the template being recorded
     * @param durationSeconds recording duration (default 3 seconds)
     */
    fun startRecording(templateName: String, durationSeconds: Int = 3) {
        recordingTemplateId = null
        startRecordingInternal(templateName, durationSeconds)
    }

    private fun startRecordingInternal(templateName: String, durationSeconds: Int) {
        // Start TFLite engine for pose inference first (so user can see themselves during countdown)
        recordingEngine = detectionEngineFactory.create(SensorType.VISION, EngineType.TFLITE)
        val tfliteEngine = recordingEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
        tfliteEngine?.let { engine ->
            engine.setCameraInfo(_isFrontCamera.value)
            engine.startPreview(DetectionConfig(
                sensorType = SensorType.VISION,
                threshold = 0.7f,
                mode = SessionMode.COUNTING,
                poseModelConfig = cachedPoseModelConfig
            ))
            viewModelScope.launch {
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

        // Register keypoint callback so detection engine forwards frames to recorder
        recordingSession.setKeypointCallback { keypoints ->
            addKeypoints(keypoints)
        }

        _recordingTemplateName.value = templateName
        _isRecording.value = true
        _recordProgress.value = 0
        _recordTargetFrames.value = durationSeconds * 10

        // Start 5-second countdown before actual recording
        _isCountingDown.value = true
        _countdownSeconds.value = 5

        countdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _countdownSeconds.value = i
                delay(1000L)
            }
            _isCountingDown.value = false
            _countdownSeconds.value = 0

            // Now start actual recording
            val recorder = TemplateRecorder()
            recorder.onProgressUpdate = { current, target ->
                _recordProgress.value = current
                _recordTargetFrames.value = target
            }
            recorder.startRecording(durationSeconds)
            templateRecorder = recorder
        }
    }

    /**
     * Process a bitmap from Camera2 (called on camera thread).
     */
    fun processBitmap(bitmap: android.graphics.Bitmap) {
        (recordingEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine)?.let { engine ->
            engine.processBitmap(bitmap)
        }
    }

    /**
     * Add a frame of keypoints during recording.
     */
    fun addKeypoints(keypoints: Array<FloatArray>) {
        templateRecorder?.addKeypoints(keypoints)
    }

    /**
     * Stop recording and save the template.
     */
    fun stopRecording(onSuccess: (Template) -> Unit, onFailure: (String) -> Unit) {
        recordingSession.clearCallback()
        recordingEngine?.stop()
        recordingEngine = null
        _keypoints.value = null
        val recorder = templateRecorder ?: run {
            onFailure("录制失败")
            return
        }
        val templateName = _recordingTemplateName.value
        val existingId = recordingTemplateId
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first()
            try {
                val template = recorder.stopAndBuildTemplate(
                    name = templateName,
                    userId = userId
                )
                if (template != null) {
                    if (existingId != null) {
                        // Update existing template with feature vector
                        val existing = templateRepository.getTemplate(existingId)
                        if (existing != null) {
                            val updated = existing.copy(featureVector = template.featureVector)
                            templateRepository.createTemplate(updated)
                        }
                    } else {
                        templateRepository.createTemplate(template)
                    }
                    _isRecording.value = false
                    templateRecorder = null
                    recordingTemplateId = null
                    onSuccess(template)
                } else {
                    _isRecording.value = false
                    templateRecorder = null
                    recordingTemplateId = null
                    onFailure("录制失败：未收集到足够的帧")
                }
            } catch (e: TemplateRecorder.InsufficientMotionException) {
                _isRecording.value = false
                templateRecorder = null
                recordingTemplateId = null
                onFailure(e.message ?: "动作幅度太小，请重新录制")
            }
        }
    }

    /**
     * Cancel the current recording session.
     */
    fun cancelRecording() {
        countdownJob?.cancel()
        countdownJob = null
        recordingSession.clearCallback()
        recordingEngine?.stop()
        recordingEngine = null
        _keypoints.value = null
        templateRecorder?.cancelRecording()
        templateRecorder = null
        _isRecording.value = false
        _isCountingDown.value = false
        _countdownSeconds.value = 5
        _recordProgress.value = 0
        _recordTargetFrames.value = 0
        _recordingTemplateName.value = ""
        recordingTemplateId = null
    }

    override fun onCleared() {
        super.onCleared()
        recordingEngine?.stop()
        recordingEngine = null
    }
}
