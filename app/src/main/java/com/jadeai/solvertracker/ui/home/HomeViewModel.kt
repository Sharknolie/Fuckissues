package com.jadeai.solvertracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val title: String = "",
    val isCreating: Boolean = false,
    val error: String? = null
)

sealed interface HomeEvent {
    data class NavigateToTaskDetail(val taskId: Long) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title, error = null) }
    }

    fun createTask() {
        val title = _uiState.value.title.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "先写一句你要解决的事") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            runCatching {
                taskRepository.create(title)
            }.onSuccess { taskId ->
                _uiState.update { it.copy(isCreating = false, title = "") }
                _events.tryEmit(HomeEvent.NavigateToTaskDetail(taskId))
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, error = "创建失败: ${e.message}" ) }
            }
        }
    }
}
