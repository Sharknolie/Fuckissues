package com.jadeai.solvertracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.data.repository.CoffeeCauseCacheRepository
import com.jadeai.solvertracker.data.repository.SolutionStepRepository
import com.jadeai.solvertracker.data.repository.TaskRepository
import com.jadeai.solvertracker.domain.model.SolutionStep
import com.jadeai.solvertracker.domain.model.Task
import com.jadeai.solvertracker.work.TaskCauseAnalysisWorkEnqueuer
import com.jadeai.solvertracker.work.TaskCauseAnalysisWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.work.WorkInfo
import javax.inject.Inject

data class TaskDetailUiState(
    val task: Task? = null,
    val steps: List<SolutionStep> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingStep: SolutionStep? = null,
    val showDeleteConfirm: Long? = null,
    val hasAnalysis: Boolean = false,
    val autoAnalysisState: AutoAnalysisState = AutoAnalysisState.IDLE,
    val autoAnalysisMessage: String = ""
)

enum class AutoAnalysisState {
    IDLE,
    ANALYZING,
    SAVED,
    SKIPPED,
    ERROR
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val stepRepository: SolutionStepRepository,
    private val causeCacheRepository: CoffeeCauseCacheRepository,
    private val workEnqueuer: TaskCauseAnalysisWorkEnqueuer
) : ViewModel() {
    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        if (taskId <= 0L) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            loadTask()
            observeSteps()
            observeAnalysisWork()
        }
    }

    private fun loadTask() {
        viewModelScope.launch {
            val task = taskRepository.getById(taskId)?.let { t ->
                val count = stepRepository.countByTask(t.id)
                t.copy(stepCount = count)
            }
            val steps = stepRepository.getByTask(taskId)
            val hasAnalysis = task?.let { causeCacheRepository.get(taskId, causeCacheRepository.signatureFor(steps)) != null } ?: false
            _uiState.update {
                it.copy(
                    task = task,
                    isLoading = false,
                    hasAnalysis = hasAnalysis,
                    autoAnalysisState = if (hasAnalysis && it.autoAnalysisState == AutoAnalysisState.IDLE) {
                        AutoAnalysisState.SAVED
                    } else {
                        it.autoAnalysisState
                    },
                    autoAnalysisMessage = if (hasAnalysis && it.autoAnalysisMessage.isBlank()) {
                        "AI 归因已保存，咖啡沉思馆可直接查看。"
                    } else {
                        it.autoAnalysisMessage
                    }
                )
            }
        }
    }

    private fun observeSteps() {
        viewModelScope.launch {
            stepRepository.observeByTask(taskId).collect { steps ->
                _uiState.update { current ->
                    current.copy(
                        steps = steps,
                        task = current.task?.copy(stepCount = steps.size)
                    )
                }
            }
        }
    }

    fun markCompleted() {
        if (taskId <= 0L) return
        viewModelScope.launch {
            taskRepository.markCompleted(taskId)
            enqueueAutoAnalysisIfNeeded()
        }
    }

    private suspend fun enqueueAutoAnalysisIfNeeded() {
        val task = taskRepository.getById(taskId) ?: return
        if (task.status != TaskStatus.COMPLETED) return
        val steps = stepRepository.getByTask(taskId).filter { it.problem.isNotBlank() || it.solution.isNotBlank() }
        if (steps.isEmpty()) {
            _uiState.update {
                it.copy(
                    autoAnalysisState = AutoAnalysisState.SKIPPED,
                    autoAnalysisMessage = "没有可分析的问题记录。"
                )
            }
            return
        }

        val signature = causeCacheRepository.signatureFor(steps)
        if (causeCacheRepository.get(taskId, signature) != null) {
            _uiState.update {
                it.copy(
                    hasAnalysis = true,
                    autoAnalysisState = AutoAnalysisState.SAVED,
                    autoAnalysisMessage = "AI 归因已保存，咖啡沉思馆可直接查看。"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                autoAnalysisState = AutoAnalysisState.ANALYZING,
                autoAnalysisMessage = "任务已完成，AI 已进入后台自动整理归因…"
            )
        }
        workEnqueuer.enqueue(taskId)
        loadTask()
    }

    private fun observeAnalysisWork() {
        viewModelScope.launch {
            workEnqueuer.workInfos(taskId).asFlow().collectLatest { infos ->
                val info = infos.firstOrNull() ?: return@collectLatest
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.RUNNING -> {
                        _uiState.update {
                            it.copy(
                                autoAnalysisState = AutoAnalysisState.ANALYZING,
                                autoAnalysisMessage = "AI 正在后台整理归因，切页面或锁屏也会尽量继续。"
                            )
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        _uiState.update {
                            it.copy(
                                hasAnalysis = true,
                                autoAnalysisState = AutoAnalysisState.SAVED,
                                autoAnalysisMessage = "AI 归因已自动保存，咖啡沉思馆可直接查看。"
                            )
                        }
                    }

                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(TaskCauseAnalysisWorker.KEY_ERROR)
                        _uiState.update {
                            it.copy(
                                autoAnalysisState = AutoAnalysisState.ERROR,
                                autoAnalysisMessage = "任务已完成，但后台归因失败：${error ?: "稍后可在咖啡沉思馆手动分析。"}"
                            )
                        }
                    }

                    WorkInfo.State.CANCELLED -> {
                        _uiState.update {
                            it.copy(
                                autoAnalysisState = AutoAnalysisState.SKIPPED,
                                autoAnalysisMessage = "后台归因已取消，稍后可在咖啡沉思馆手动分析。"
                            )
                        }
                    }
                }
            }
        }
    }

    fun showAddDialog() = _uiState.update { it.copy(showAddDialog = true) }
    fun dismissAddDialog() = _uiState.update { it.copy(showAddDialog = false) }

    fun addStep(problem: String, solution: String) {
        if (taskId <= 0L) return
        if (problem.isBlank() || solution.isBlank()) return
        viewModelScope.launch {
            stepRepository.add(taskId, problem, solution)
            _uiState.update { it.copy(showAddDialog = false) }
            loadTask()
        }
    }

    fun showEditDialog(step: SolutionStep) =
        _uiState.update { it.copy(showEditDialog = true, editingStep = step) }
    fun dismissEditDialog() =
        _uiState.update { it.copy(showEditDialog = false, editingStep = null) }

    fun updateStep(problem: String, solution: String) {
        val step = _uiState.value.editingStep ?: return
        viewModelScope.launch {
            stepRepository.update(step.copy(problem = problem, solution = solution))
            _uiState.update { it.copy(showEditDialog = false, editingStep = null) }
        }
    }

    fun updateStep(stepId: Long, problem: String, solution: String) {
        if (problem.isBlank() || solution.isBlank()) return
        val step = _uiState.value.steps.firstOrNull { it.id == stepId } ?: return
        viewModelScope.launch {
            stepRepository.update(step.copy(problem = problem, solution = solution))
        }
    }

    fun confirmDelete(stepId: Long) = _uiState.update { it.copy(showDeleteConfirm = stepId) }
    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirm = null) }

    fun deleteStep(stepId: Long) {
        viewModelScope.launch {
            stepRepository.delete(stepId)
            _uiState.update { it.copy(showDeleteConfirm = null) }
            loadTask()
        }
    }
}
