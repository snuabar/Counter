package com.snuabar.counter.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.data.local.prefs.ThemeMode
import com.snuabar.counter.data.local.prefs.ThemePreferences
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
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _threshold = MutableStateFlow(0.7f)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM).asStateFlow()

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
                (themeMode as MutableStateFlow).value = mode
            }
        }
    }

    fun setThreshold(value: Float) {
        _threshold.value = value.coerceIn(0.1f, 1.0f)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun exportData(): String {
        var result = ""
        viewModelScope.launch {
            result = backupRepository.exportToJson()
        }
        return result
    }

    fun importData(jsonString: String) {
        viewModelScope.launch {
            backupRepository.importFromJson(jsonString)
        }
    }
}
