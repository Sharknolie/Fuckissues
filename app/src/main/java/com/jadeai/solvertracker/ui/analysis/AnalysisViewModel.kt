package com.jadeai.solvertracker.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.repository.AnalysisRepository
import com.jadeai.solvertracker.data.repository.AnalysisException
import com.jadeai.solvertracker.data.repository.ApiKeyMissingException
import com.jadeai.solvertracker.data.repository.TaskRepository
import com.jadeai.solvertracker.domain.model.AnalysisResult
import com.jadeai.solvertracker.domain.model.CategoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisUiState(
    val isLoading: Boolean = false,
    val result: AnalysisResult? = null,
    val error: String? = null,
    val taskTitle: String = ""
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val analysisRepository: AnalysisRepository
) : ViewModel() {
    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: 0L

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        loadTaskAndCache()
    }

    private fun loadTaskAndCache() {
        viewModelScope.launch {
            val task = taskRepository.getById(taskId)
            _uiState.update { it.copy(taskTitle = task?.title ?: "") }

            val cached = analysisRepository.getCachedResult(taskId)
            if (cached != null) {
                _uiState.update { it.copy(result = cached) }
            }
        }
    }

    fun startAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val title = _uiState.value.taskTitle
                val result = analysisRepository.analyze(taskId, title)
                _uiState.update { it.copy(isLoading = false, result = result, error = null) }
            } catch (e: ApiKeyMissingException) {
                _uiState.update { it.copy(isLoading = false, error = "请先在设置页填入 API Key") }
            } catch (e: AnalysisException) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "分析失败: ${e.message}") }
            }
        }
    }
}
