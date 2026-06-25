package com.snuabar.counter.ui.screen.template

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.*
import com.snuabar.counter.core.template.RecordingSession
import com.snuabar.counter.core.template.TemplateRecorder
import com.snuabar.counter.domain.model.SensorType
import com.snuabar.counter.domain.model.Template
import com.snuabar.counter.domain.model.TemplateType
import com.snuabar.counter.domain.repository.TemplateRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateRepository: TemplateRepository,
    private val userRepository: UserRepository,
    private val recordingSession: RecordingSession,
    private val detectionEngineFactory: DetectionEngineFactory
) : ViewModel() {

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val _showAddTemplateDialog = MutableStateFlow(false)
    val showAddTemplateDialog: StateFlow<Boolean> = _showAddTemplateDialog.asStateFlow()

    private val _newTemplateName = MutableStateFlow("")
    val newTemplateName: StateFlow<String> = _newTemplateName.asStateFlow()

    private val _selectedSensorType = MutableStateFlow(SensorType.VISION)
    val selectedSensorType: StateFlow<SensorType> = _selectedSensorType.asStateFlow()

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordProgress = MutableStateFlow(0)
    val recordProgress: StateFlow<Int> = _recordProgress.asStateFlow()

    private val _recordTargetFrames = MutableStateFlow(0)
    val recordTargetFrames: StateFlow<Int> = _recordTargetFrames.asStateFlow()

    private val _recordingTemplateName = MutableStateFlow("")
    val recordingTemplateName: StateFlow<String> = _recordingTemplateName.asStateFlow()

    // Keypoints for visualization
    private val _keypoints = MutableStateFlow<Array<FloatArray>?>(null)
    val keypoints: StateFlow<Array<FloatArray>?> = _keypoints.asStateFlow()

    // Detection engine for recording
    private var recordingEngine: DetectionEngine? = null
    // TemplateRecorder instance for active recording
    private var templateRecorder: TemplateRecorder? = null
    private var recordingTemplateId: Long? = null

    init {
        viewModelScope.launch {
            templateRepository.ensureBuiltinTemplates()
            templateRepository.getAllTemplates().collect { list ->
                _templates.value = list
            }
        }
    }

    fun createTemplate(name: String, sensorType: SensorType) {
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first()
            val template = Template(
                userId = userId,
                name = name,
                type = TemplateType.CUSTOM,
                sensorType = sensorType
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
        val recorder = TemplateRecorder()
        recorder.onProgressUpdate = { current, target ->
            _recordProgress.value = current
            _recordTargetFrames.value = target
        }
        recorder.startRecording(durationSeconds)
        templateRecorder = recorder

        // Start TFLite detection engine to process camera frames
        recordingEngine = detectionEngineFactory.create(SensorType.VISION, EngineType.TFLITE)
        val tfliteEngine = recordingEngine as? com.snuabar.counter.core.detection.tflite.TFLiteDetectionEngine
        tfliteEngine?.let { engine ->
            engine.start(DetectionConfig(
                sensorType = SensorType.VISION,
                threshold = 0.7f,
                mode = com.snuabar.counter.domain.model.SessionMode.COUNTING,
                actionType = com.snuabar.counter.domain.model.ActionType.CUSTOM
            ))
            viewModelScope.launch {
                engine.keypoints.collect { kps ->
                    _keypoints.value = kps
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
    }

    /**
     * Get the image analyzer for camera integration.
     */
    fun getImageAnalyzer(): ImageAnalysis.Analyzer? {
        return recordingEngine as? ImageAnalysis.Analyzer
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
    fun stopRecording(onSuccess: (Template) -> Unit, onFailure: () -> Unit) {
        recordingSession.clearCallback()
        recordingEngine?.stop()
        recordingEngine = null
        _keypoints.value = null
        val recorder = templateRecorder ?: run {
            onFailure()
            return
        }
        val templateName = _recordingTemplateName.value
        val existingId = recordingTemplateId
        viewModelScope.launch {
            val userId = userRepository.currentUserId.first()
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
                onFailure()
            }
        }
    }

    /**
     * Cancel the current recording session.
     */
    fun cancelRecording() {
        recordingSession.clearCallback()
        recordingEngine?.stop()
        recordingEngine = null
        _keypoints.value = null
        templateRecorder?.cancelRecording()
        templateRecorder = null
        _isRecording.value = false
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
