package com.jadeai.solvertracker.ui.coffee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseContent
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseItemDto
import com.jadeai.solvertracker.data.remote.dto.Message
import com.jadeai.solvertracker.data.repository.AnalysisException
import com.jadeai.solvertracker.data.repository.ApiKeyMissingException
import com.jadeai.solvertracker.data.repository.CachedCoffeeChatMessage
import com.jadeai.solvertracker.data.repository.CoffeeCauseCacheRepository
import com.jadeai.solvertracker.data.repository.CoffeeChatCacheRepository
import com.jadeai.solvertracker.data.repository.CoffeeRepository
import com.jadeai.solvertracker.data.repository.SolutionStepRepository
import com.jadeai.solvertracker.data.repository.TaskRepository
import com.jadeai.solvertracker.domain.model.SolutionStep
import com.jadeai.solvertracker.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoffeeTaskOption(
    val id: Long,
    val title: String,
    val stepCount: Int
)

data class CoffeeStepItem(
    val id: Long,
    val index: Int,
    val problem: String,
    val solution: String
)

data class CoffeeChatMessage(
    val role: String,
    val content: String
)

sealed interface CoffeeChatStreamEvent {
    data class Delta(val content: String) : CoffeeChatStreamEvent
}

data class CoffeeUiState(
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val tasks: List<CoffeeTaskOption> = emptyList(),
    val selectedTaskId: Long? = null,
    val selectedTaskTitle: String = "",
    val selectedSteps: List<CoffeeStepItem> = emptyList(),
    val causeResult: CoffeeCauseContent? = null,
    val causeAnalyzedAt: Long? = null,
    val selectedCauseItem: CoffeeCauseItemDto? = null,
    val isChatOpen: Boolean = false,
    val isChatSending: Boolean = false,
    val selectedChatStepIndex: Int? = null,
    val chatMessages: List<CoffeeChatMessage> = emptyList(),
    val chatError: String? = null,
    val error: String? = null,
    val hasApiKey: Boolean = true
) {
    val totalProblems: Int get() = selectedSteps.size
}

@HiltViewModel
class CoffeeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val stepRepository: SolutionStepRepository,
    private val coffeeRepository: CoffeeRepository,
    private val causeCacheRepository: CoffeeCauseCacheRepository,
    private val chatCacheRepository: CoffeeChatCacheRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoffeeUiState())
    val uiState: StateFlow<CoffeeUiState> = _uiState.asStateFlow()
    private val _chatStreamEvents = MutableSharedFlow<CoffeeChatStreamEvent>(extraBufferCapacity = 256)
    val chatStreamEvents: SharedFlow<CoffeeChatStreamEvent> = _chatStreamEvents.asSharedFlow()

    private var allTasks: List<Task> = emptyList()
    private var stepsByTaskId: Map<Long, List<SolutionStep>> = emptyMap()

    init {
        viewModelScope.launch {
            taskRepository.observeAll().collect { tasks ->
                allTasks = tasks.sortedByDescending { it.createdAt }
                val stepMap = buildMap {
                    allTasks.forEach { task ->
                        put(task.id, stepRepository.getByTask(task.id))
                    }
                }
                stepsByTaskId = stepMap

                val options = allTasks
                    .filter { task -> stepMap[task.id].orEmpty().isNotEmpty() }
                    .map { task ->
                        CoffeeTaskOption(
                            id = task.id,
                            title = task.title,
                            stepCount = stepMap[task.id].orEmpty().size
                        )
                    }
                val previousSelectedId = _uiState.value.selectedTaskId
                val selectedId = previousSelectedId
                    ?.takeIf { id -> options.any { it.id == id } }
                    ?: options.firstOrNull()?.id
                val selectedChanged = selectedId != previousSelectedId

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tasks = options,
                        selectedTaskId = selectedId,
                        error = null
                    )
                }
                refreshSelectedTask(clearResult = selectedChanged)
            }
        }
    }

    fun selectTask(taskId: Long) {
        if (_uiState.value.selectedTaskId == taskId) return
        _uiState.update {
            it.copy(
                selectedTaskId = taskId,
                causeResult = null,
                causeAnalyzedAt = null,
                selectedCauseItem = null,
                isChatOpen = false,
                selectedChatStepIndex = null,
                chatMessages = emptyList(),
                chatError = null,
                error = null
            )
        }
        viewModelScope.launch { refreshSelectedTask(clearResult = true) }
    }

    fun analyzeSelectedTask() {
        val state = _uiState.value
        val taskId = state.selectedTaskId ?: return
        val task = allTasks.firstOrNull { it.id == taskId } ?: return
        val steps = stepsByTaskId[taskId].orEmpty().filter { it.problem.isNotBlank() || it.solution.isNotBlank() }
        if (steps.isEmpty()) return
        val signature = causeCacheRepository.signatureFor(steps)

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null, selectedCauseItem = null) }
            try {
                val result = coffeeRepository.analyzeTaskCauses(task.title, steps)
                val analyzedAt = causeCacheRepository.save(taskId, signature, result)
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        causeResult = result,
                        causeAnalyzedAt = analyzedAt,
                        error = null,
                        hasApiKey = true
                    )
                }
            } catch (_: ApiKeyMissingException) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        causeResult = null,
                        causeAnalyzedAt = null,
                        hasApiKey = false,
                        error = null
                    )
                }
            } catch (e: AnalysisException) {
                _uiState.update { it.copy(isAnalyzing = false, error = e.message ?: "分析失败") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = "分析失败: ${e.message}") }
            }
        }
    }

    fun selectCauseItem(stepIndex: Int) {
        val item = _uiState.value.causeResult?.items?.firstOrNull { it.stepIndex == stepIndex }
        _uiState.update { it.copy(selectedCauseItem = item) }
    }

    fun clearCauseItem() {
        _uiState.update { it.copy(selectedCauseItem = null) }
    }

    fun openChat() {
        _uiState.update {
            it.copy(
                isChatOpen = true,
                selectedChatStepIndex = it.selectedChatStepIndex,
                chatMessages = if (it.chatMessages.isEmpty()) {
                    listOf(
                        CoffeeChatMessage(
                            role = "assistant",
                            content = CHAT_INTRO_MESSAGE
                        )
                    )
                } else {
                    it.chatMessages
                },
                chatError = null
            )
        }
    }

    fun closeChat() {
        _uiState.update { it.copy(isChatOpen = false, chatError = null) }
    }

    fun selectChatStep(stepIndex: Int?) {
        _uiState.update { it.copy(selectedChatStepIndex = stepIndex, chatError = null) }
        saveChatSnapshot()
    }

    fun sendChatMessage(content: String) {
        val question = content.trim()
        if (question.isBlank()) return
        val state = _uiState.value
        if (state.isChatSending) return
        val selectedItem = state.causeResult?.items?.firstOrNull { it.stepIndex == state.selectedChatStepIndex }
        val history = (state.chatMessages + CoffeeChatMessage(role = "user", content = question)).takeLast(12)
        val streamingMessages = history + CoffeeChatMessage(role = "assistant", content = "")

        _uiState.update {
            it.copy(
                isChatSending = true,
                chatMessages = streamingMessages,
                chatError = null,
                hasApiKey = true
            )
        }
        saveChatSnapshot()

        viewModelScope.launch {
            try {
                val requestMessages = history
                    .filterNot { it.role == "assistant" && it.content == CHAT_INTRO_MESSAGE }
                    .map { Message(role = it.role, content = it.content) }
                val answer = coffeeRepository.streamCauseDiscussion(
                    taskTitle = state.selectedTaskTitle,
                    causeResult = state.causeResult,
                    selectedItem = selectedItem,
                    messages = requestMessages,
                    onDelta = { delta -> emitChatDelta(delta) }
                )
                _uiState.update {
                    it.copy(
                        isChatSending = false,
                        chatMessages = replaceLastAssistantMessage(it.chatMessages, cleanChatMarkdown(answer)).takeLast(16),
                        chatError = null,
                        hasApiKey = true
                    )
                }
                saveChatSnapshot()
            } catch (_: ApiKeyMissingException) {
                _uiState.update {
                    it.copy(
                        isChatSending = false,
                        hasApiKey = false,
                        chatError = "请先配置 DeepSeek API Key"
                    )
                }
            } catch (e: AnalysisException) {
                _uiState.update { it.copy(isChatSending = false, chatError = e.message ?: "AI 对话失败") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isChatSending = false, chatError = "AI 对话失败: ${e.message}") }
            }
        }
    }

    fun classifyNow() = analyzeSelectedTask()

    private suspend fun refreshSelectedTask(clearResult: Boolean) {
        val selectedId = _uiState.value.selectedTaskId
        val task = allTasks.firstOrNull { it.id == selectedId }
        val rawSteps = selectedId?.let { stepsByTaskId[it].orEmpty() }.orEmpty()
        val steps = rawSteps.mapIndexed { index, step ->
            CoffeeStepItem(
                id = step.id,
                index = index + 1,
                problem = step.problem,
                solution = step.solution
            )
        }
        val cached = selectedId?.let { id ->
            causeCacheRepository.get(id, causeCacheRepository.signatureFor(rawSteps))
        }
        val cachedChat = selectedId?.let { id -> chatCacheRepository.get(id) }

        _uiState.update {
            val shouldReplaceResult = clearResult || it.causeResult == null
            it.copy(
                selectedTaskTitle = task?.title.orEmpty(),
                selectedSteps = steps,
                causeResult = if (shouldReplaceResult) cached?.result else it.causeResult,
                causeAnalyzedAt = if (shouldReplaceResult) cached?.analyzedAt else it.causeAnalyzedAt,
                selectedCauseItem = if (clearResult) null else it.selectedCauseItem,
                isChatOpen = if (clearResult) false else it.isChatOpen,
                selectedChatStepIndex = if (clearResult) cachedChat?.selectedStepIndex else it.selectedChatStepIndex,
                chatMessages = if (clearResult) {
                    cachedChat?.messages?.map { message ->
                        CoffeeChatMessage(role = message.role, content = message.content)
                    }.orEmpty()
                } else {
                    it.chatMessages
                },
                chatError = if (clearResult) null else it.chatError
            )
        }
    }

    private fun cleanChatMarkdown(value: String): String = value
        .replace("null", "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace("`", "")
        .trim()

    private suspend fun emitChatDelta(delta: String) {
        val content = delta.replace("null", "")
        if (content.isNotEmpty()) {
            _chatStreamEvents.emit(CoffeeChatStreamEvent.Delta(content))
        }
    }

    private fun saveChatSnapshot() {
        val state = _uiState.value
        val taskId = state.selectedTaskId ?: return
        val messages = state.chatMessages
            .filterNot { it.role == "assistant" && it.content == CHAT_INTRO_MESSAGE }
            .filter { it.content.isNotBlank() }
        viewModelScope.launch {
            chatCacheRepository.save(
                taskId = taskId,
                messages = messages.map { message ->
                    CachedCoffeeChatMessage(role = message.role, content = message.content)
                },
                selectedStepIndex = state.selectedChatStepIndex
            )
        }
    }

    private fun replaceLastAssistantMessage(
        messages: List<CoffeeChatMessage>,
        content: String
    ): List<CoffeeChatMessage> {
        val lastAssistantIndex = messages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIndex < 0) return messages + CoffeeChatMessage(role = "assistant", content = content)
        return messages.mapIndexed { index, message ->
            if (index == lastAssistantIndex) message.copy(content = content) else message
        }
    }

    companion object {
        private const val CHAT_INTRO_MESSAGE = "可以，默认先讨论整个任务。如果你想聚焦某一条问题，可以在上方选择。"
    }

}
