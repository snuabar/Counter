package com.snuabar.counter.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.core.detection.tflite.PoseModelConfig
import com.snuabar.counter.data.local.prefs.BackupPreferences
import com.snuabar.counter.data.local.prefs.DetectionPreferences
import com.snuabar.counter.data.local.prefs.ThemeMode
import com.snuabar.counter.data.local.prefs.ThemePreferences
import com.snuabar.counter.data.remote.WebDAVRemoteBackupDataSource
import com.snuabar.counter.data.repository.BackupRepository
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val themePreferences: ThemePreferences,
    private val detectionPreferences: DetectionPreferences,
    private val backupRepository: BackupRepository,
    private val backupPreferences: BackupPreferences,
    private val webDAVRemoteBackupDataSource: WebDAVRemoteBackupDataSource
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _poseModelConfig = MutableStateFlow(PoseModelConfig.STANDARD)
    val poseModelConfig: StateFlow<PoseModelConfig> = _poseModelConfig.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _voiceAnnouncement = MutableStateFlow(false)
    val voiceAnnouncement: StateFlow<Boolean> = _voiceAnnouncement.asStateFlow()

    private val _gpuAcceleration = MutableStateFlow(true)
    val gpuAcceleration: StateFlow<Boolean> = _gpuAcceleration.asStateFlow()

    // WebDAV settings
    private val _webDavBaseUrl = MutableStateFlow("")
    val webDavBaseUrl: StateFlow<String> = _webDavBaseUrl.asStateFlow()

    private val _webDavUsername = MutableStateFlow("")
    val webDavUsername: StateFlow<String> = _webDavUsername.asStateFlow()

    private val _webDavPassword = MutableStateFlow("")
    val webDavPassword: StateFlow<String> = _webDavPassword.asStateFlow()

    private val _isWebDavConnected = MutableStateFlow(false)
    val isWebDavConnected: StateFlow<Boolean> = _isWebDavConnected.asStateFlow()

    private val _isWebDavUploading = MutableStateFlow(false)
    val isWebDavUploading: StateFlow<Boolean> = _isWebDavUploading.asStateFlow()

    private val _webDavMessage = MutableStateFlow("")
    val webDavMessage: StateFlow<String> = _webDavMessage.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.ensureDefaultUser()
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    _currentUser.value = userRepository.getUser(it)
                } ?: run {
                    _currentUser.value = null
                }
            }
        }
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            detectionPreferences.poseModelConfigFlow.collect { config ->
                _poseModelConfig.value = config
            }
        }
        // Collect WebDAV preferences
        viewModelScope.launch {
            backupPreferences.baseUrlFlow.collect { url ->
                _webDavBaseUrl.value = url
            }
        }
        viewModelScope.launch {
            backupPreferences.usernameFlow.collect { username ->
                _webDavUsername.value = username
            }
        }
        viewModelScope.launch {
            backupPreferences.passwordFlow.collect { password ->
                _webDavPassword.value = password
            }
        }
        viewModelScope.launch {
            detectionPreferences.voiceAnnouncementFlow.collect { enabled ->
                _voiceAnnouncement.value = enabled
            }
        }
        viewModelScope.launch {
            detectionPreferences.gpuAccelerationFlow.collect { enabled ->
                _gpuAcceleration.value = enabled
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setPoseModelConfig(config: PoseModelConfig) {
        viewModelScope.launch {
            detectionPreferences.setPoseModelConfig(config)
        }
    }

    fun setVoiceAnnouncement(enabled: Boolean) {
        viewModelScope.launch {
            detectionPreferences.setVoiceAnnouncement(enabled)
        }
    }

    fun setGpuAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            detectionPreferences.setGpuAcceleration(enabled)
        }
    }

    fun exportData(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.exportToJson()
            onResult(result)
        }
    }

    fun importData(jsonString: String) {
        viewModelScope.launch {
            backupRepository.importFromJson(jsonString)
        }
    }

    // WebDAV methods
    fun setWebDavConfig(baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            backupPreferences.setWebDavConfig(baseUrl, username, password)
        }
    }

    fun testWebDavConnection() {
        viewModelScope.launch {
            val connected = webDAVRemoteBackupDataSource.testConnection()
            _isWebDavConnected.value = connected
            _webDavMessage.value = if (connected) "连接成功" else "连接失败，请检查配置"
        }
    }

    fun uploadToWebDav() {
        viewModelScope.launch {
            _isWebDavUploading.value = true
            try {
                val json = backupRepository.exportToJson()
                val success = webDAVRemoteBackupDataSource.upload(json, "counter_backup.json")
                _webDavMessage.value = if (success) "上传备份成功" else "上传备份失败"
            } catch (e: Exception) {
                _webDavMessage.value = "上传备份失败: ${e.message}"
            } finally {
                _isWebDavUploading.value = false
            }
        }
    }

    fun restoreFromWebDav() {
        viewModelScope.launch {
            try {
                val json = webDAVRemoteBackupDataSource.download("counter_backup.json")
                if (json != null) {
                    backupRepository.importFromJson(json)
                    _webDavMessage.value = "从远程恢复成功"
                } else {
                    _webDavMessage.value = "从远程恢复失败：未找到备份文件"
                }
            } catch (e: Exception) {
                _webDavMessage.value = "从远程恢复失败: ${e.message}"
            }
        }
    }
}
