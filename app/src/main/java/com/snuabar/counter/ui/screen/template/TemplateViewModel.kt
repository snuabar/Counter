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
    private val detectionPreferences: com.snuabar.counter.data.local.prefs.DetectionPreferences,
    private val cameraPreferences: com.snuabar.counter.data.local.prefs.CameraPreferences
) : ViewModel() {

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val _showAddTemplateDialog = MutableStateFlow(false)
    val showAddTemplateDialog: StateFlow<Boolean> = _showAddTemplateDialog.asStateFlow()

    private val _showEditTemplateDialog = MutableStateFlow(false)
    val showEditTemplateDialog: StateFlow<Boolean> = _showEditTemplateDialog.asStateFlow()

    private val _editingTemplateId = MutableStateFlow<Long?>(null)
    val editingTemplateId: StateFlow<Long?> = _editingTemplateId.asStateFlow()

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

    private val _isRecordingComplete = MutableStateFlow(false)
    val isRecordingComplete: StateFlow<Boolean> = _isRecordingComplete.asStateFlow()

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

        // Enumerate available cameras (same logic as CountingViewModel)
        viewModelScope.launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraInfo = mutableMapOf<String, String>()
                val usedNames = mutableMapOf<String, Int>()
                for (cameraId in cameraManager.cameraIdList) {
                    try {
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        
                        Log.d("TemplateCameraDebug", "Camera[$cameraId]: facing=$lensFacing, focal=${focalLengths?.toList()}")
                        
                        val facingStr = when (lensFacing) {
                            CameraCharacteristics.LENS_FACING_BACK -> "后置"
                            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                            else -> "未知"
                        }
                        
                        val cameraType = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK &&
                            focalLengths != null && focalLengths.isNotEmpty()) {
                            val focalLength = focalLengths[0]
                            when {
                                focalLength < 4f -> "超广角"
                                focalLength < 7f -> "广角"
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
                        
                        val count = usedNames.getOrDefault(baseName, 0)
                        usedNames[baseName] = count + 1
                        val displayName = if (count > 0) "$baseName ${count + 1}" else baseName
                        
                        cameraInfo[cameraId] = displayName
                        
                        // Check if this logical camera has physical cameras (multi-camera system)
                        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        if (capabilities != null) {
                            Log.d("TemplateCameraDebug", "Camera[$cameraId] capabilities: ${capabilities.toList()}")
                            if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                                val physicalIds = characteristics.physicalCameraIds
                                Log.d("TemplateCameraDebug", "Camera[$cameraId] has LOGICAL_MULTI_CAMERA, physicalIds: ${physicalIds.toList()}")
                                for (physicalId in physicalIds) {
                                    try {
                                        val physChars = cameraManager.getCameraCharacteristics(physicalId)
                                        val physFacing = physChars.get(CameraCharacteristics.LENS_FACING)
                                        val physFocalLengths = physChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                                        
                                        // Skip physical camera if it has same focal length as the logical camera (duplicate)
                                        val logicalFocal = focalLengths?.getOrNull(0)
                                        val physicalFocal = physFocalLengths?.getOrNull(0)
                                        if (logicalFocal != null && physicalFocal != null && 
                                            kotlin.math.abs(logicalFocal - physicalFocal) < 0.01f) {
                                            Log.d("TemplateCameraDebug", "Skipping duplicate physical camera $physicalId (focal=$physicalFocal)")
                                            continue
                                        }
                                        
                                        val physFacingStr = when (physFacing) {
                                            CameraCharacteristics.LENS_FACING_BACK -> "后置"
                                            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
                                            else -> "未知"
                                        }
                                        
                                        val physType = if (physFacing == CameraCharacteristics.LENS_FACING_BACK &&
                                            physFocalLengths != null && physFocalLengths.isNotEmpty()) {
                                            val physFocal = physFocalLengths[0]
                                            when {
                                                physFocal < 4f -> "超广角"
                                                physFocal < 7f -> "广角"
                                                else -> "长焦"
                                            }
                                        } else {
                                            ""
                                        }
                                        
                                        val physBaseName = if (physType.isNotEmpty()) {
                                            "$physFacingStr $physType"
                                        } else {
                                            "$physFacingStr 摄像头"
                                        }
                                        
                                        val physCount = usedNames.getOrDefault(physBaseName, 0)
                                        usedNames[physBaseName] = physCount + 1
                                        val physDisplayName = if (physCount > 0) "$physBaseName ${physCount + 1}" else physBaseName
                                        
                                        cameraInfo[physicalId] = physDisplayName
                                        Log.d("TemplateCameraDebug", "Added physical camera $physicalId: $physDisplayName")
                                    } catch (e: Exception) {
                                        Log.e("TemplateCameraDebug", "Failed to get physical camera $physicalId", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TemplateCameraDebug", "Failed to get characteristics for camera $cameraId", e)
                    }
                }
                Log.d("TemplateCameraDebug", "=== Final camera list: $cameraInfo ===")
                _availableCameras.value = cameraInfo
                
                // Restore saved camera selection
                try {
                    val savedCameraId = cameraPreferences.templateCameraId.first()
                    val savedIsFront = cameraPreferences.templateIsFront.first()
                    if (savedCameraId != null && cameraInfo.containsKey(savedCameraId)) {
                        _selectedCameraId.value = savedCameraId
                        _isFrontCamera.value = savedIsFront
                        recordingEngine?.setCameraInfo(savedIsFront)
                        Log.d("TemplateCameraDebug", "Restored template camera: $savedCameraId, isFront=$savedIsFront")
                    }
                } catch (e: Exception) {
                    Log.e("TemplateCameraDebug", "Failed to restore camera selection", e)
                }
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
            val templateId = templateRepository.createTemplate(template)
            _showAddTemplateDialog.value = false
            _newTemplateName.value = ""
            // 自动进入录制画面
            startRecordingForTemplate(templateId, name)
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
        }
    }

    fun startEditTemplate(template: Template) {
        _editingTemplateId.value = template.id
        _newTemplateName.value = template.name
        _selectedSensorType.value = template.sensorType
        _selectedSessionMode.value = template.mode
        _showEditTemplateDialog.value = true
    }

    fun updateTemplate(templateId: Long, name: String, sensorType: SensorType, mode: SessionMode) {
        viewModelScope.launch {
            val existing = templateRepository.getTemplate(templateId)
            if (existing != null) {
                val updated = existing.copy(
                    name = name,
                    sensorType = sensorType,
                    mode = mode
                )
                templateRepository.createTemplate(updated)
            }
            _showEditTemplateDialog.value = false
            _editingTemplateId.value = null
            _newTemplateName.value = ""
        }
    }

    fun updateTemplateName(templateId: Long, name: String) {
        viewModelScope.launch {
            val existing = templateRepository.getTemplate(templateId)
            if (existing != null) {
                val updated = existing.copy(name = name)
                templateRepository.createTemplate(updated)
            }
            _showEditTemplateDialog.value = false
            _editingTemplateId.value = null
            _newTemplateName.value = ""
        }
    }

    fun cancelEditTemplate() {
        _showEditTemplateDialog.value = false
        _editingTemplateId.value = null
        _newTemplateName.value = ""
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
        val newIsFront = !_isFrontCamera.value
        _isFrontCamera.value = newIsFront
        // Find first camera matching the new facing direction
        val defaultId = findDefaultCameraId(newIsFront)
        _selectedCameraId.value = defaultId
        recordingEngine?.setCameraInfo(newIsFront)
        // Persist selection
        if (defaultId != null) {
            viewModelScope.launch {
                cameraPreferences.setTemplateCamera(defaultId, newIsFront)
            }
        }
    }

    /**
     * Find the first camera ID matching the requested facing direction.
     */
    private fun findDefaultCameraId(isFront: Boolean): String? {
        val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        for (cameraId in cameraManager.cameraIdList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    return cameraId
                }
            } catch (e: Exception) {
                Log.e("CameraDebug", "Failed to get characteristics for $cameraId", e)
            }
        }
        return null
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
                // Persist selection
                viewModelScope.launch {
                    cameraPreferences.setTemplateCamera(cameraId, isFront)
                }
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
        _isRecordingComplete.value = false
        _recordProgress.value = 0
        val initialFps = if (_fps.value > 0) _fps.value else 30
        _recordTargetFrames.value = durationSeconds * initialFps

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
                android.util.Log.d("TemplateViewModel", "Progress: $current / $target")
                _recordProgress.value = current
                _recordTargetFrames.value = target
                _isRecordingComplete.value = (current >= target)
            }
            // Use actual camera FPS if available, otherwise default to 30
            val actualFps = if (_fps.value > 0) _fps.value else 30
            android.util.Log.d("TemplateViewModel", "Starting recording with actualFps=$actualFps, durationSeconds=$durationSeconds")
            recorder.startRecording(durationSeconds, actualFps)
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
                    android.util.Log.d("TemplateViewModel", "Template built: name=$templateName, featureVector=${template.featureVector?.size ?: 0} bytes, frames=${templateRecorder?.recordedFrames ?: 0}")
                    if (existingId != null) {
                        // Update existing template with feature vector
                        val existing = templateRepository.getTemplate(existingId)
                        if (existing != null) {
                            val updated = existing.copy(
                                featureVector = template.featureVector,
                                keypointSequence = template.keypointSequence
                            )
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
        _isRecordingComplete.value = false
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
