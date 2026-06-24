package com.snuabar.counter.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snuabar.counter.domain.model.CountingSession
import com.snuabar.counter.domain.repository.CountingSessionRepository
import com.snuabar.counter.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val countingSessionRepository: CountingSessionRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<CountingSession>>(emptyList())
    val sessions: StateFlow<List<CountingSession>> = _sessions.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentUserId.collect { userId ->
                userId?.let {
                    countingSessionRepository.getSessionsByUserId(it).collect { list ->
                        _sessions.value = list
                        _totalCount.value = list.sumOf { it.finalCount }
                    }
                }
            }
        }
    }
}
