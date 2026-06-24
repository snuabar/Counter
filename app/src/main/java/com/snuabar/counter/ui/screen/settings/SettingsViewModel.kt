package com.snuabar.counter.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _threshold = MutableStateFlow(0.7f)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

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
    }

    fun setThreshold(value: Float) {
        _threshold.value = value.coerceIn(0.1f, 1.0f)
    }
}
