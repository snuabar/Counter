package com.snuabar.counter.ui.screen.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.domain.model.User
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val users: Flow<List<User>> = userRepository.getAllUsers()
    val currentUserId: Flow<Long?> = userRepository.currentUserId

    private val _showAddUserDialog = MutableStateFlow(false)
    val showAddUserDialog: StateFlow<Boolean> = _showAddUserDialog.asStateFlow()

    private val _newUserName = MutableStateFlow("")
    val newUserName: StateFlow<String> = _newUserName.asStateFlow()

    fun createUser(name: String) {
        viewModelScope.launch {
            userRepository.createUser(name)
            _showAddUserDialog.value = false
            _newUserName.value = ""
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            userRepository.deleteUser(userId)
        }
    }

    fun switchUser(userId: Long) {
        viewModelScope.launch {
            userRepository.setCurrentUser(userId)
        }
    }

    fun setShowAddUserDialog(show: Boolean) {
        _showAddUserDialog.value = show
    }

    fun setNewUserName(name: String) {
        _newUserName.value = name
    }
}
