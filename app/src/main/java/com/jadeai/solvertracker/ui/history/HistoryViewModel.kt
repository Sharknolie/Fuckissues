package com.jadeai.solvertracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.data.repository.SolutionStepRepository
import com.jadeai.solvertracker.data.repository.TaskRepository
import com.jadeai.solvertracker.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val inProgress: List<Task> = emptyList(),
    val completed: List<Task> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val stepRepository: SolutionStepRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.observeAll().collect { tasks ->
                val withCounts = tasks.map { task ->
                    val count = stepRepository.countByTask(task.id)
                    task.copy(stepCount = count)
                }
                val inProgress = withCounts.filter { it.status == TaskStatus.IN_PROGRESS }
                val completed = withCounts.filter { it.status == TaskStatus.COMPLETED }
                _uiState.update {
                    it.copy(
                        inProgress = inProgress,
                        completed = completed,
                        isLoading = false
                    )
                }
            }
        }
    }
}
