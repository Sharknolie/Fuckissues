package com.jadeai.solvertracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.remote.DeepSeekApiService
import com.jadeai.solvertracker.data.remote.dto.AnalysisRequest
import com.jadeai.solvertracker.data.remote.dto.Message
import com.jadeai.solvertracker.data.remote.dto.ResponseFormat
import com.jadeai.solvertracker.data.repository.buildDeepSeekChatUrl
import com.jadeai.solvertracker.data.repository.buildDeepSeekModelsUrl
import com.jadeai.solvertracker.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = SettingsRepository.DEFAULT_BASE_URL,
    val model: String = SettingsRepository.DEFAULT_MODEL,
    val models: List<String> = emptyList(),
    val isSaved: Boolean = false,
    val isTesting: Boolean = false,
    val isLoadingModels: Boolean = false,
    val testMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiService: dagger.Lazy<DeepSeekApiService>
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey = settingsRepository.getApiKey() ?: ""
            val baseUrl = settingsRepository.getBaseUrl()
            val model = settingsRepository.getModel()
            _uiState.update {
                it.copy(apiKey = apiKey, baseUrl = baseUrl, model = model)
            }
        }
    }

    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value, isSaved = false, testMessage = null, error = null) }
    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value, isSaved = false, testMessage = null, error = null) }
    fun updateModel(value: String) = _uiState.update { it.copy(model = value, isSaved = false, testMessage = null, error = null) }

    fun loadModels() {
        val current = _uiState.value
        if (current.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先填写 API Key，再拉取模型列表", testMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, error = null, testMessage = null) }
            runCatching {
                apiService.get().models(
                    buildDeepSeekModelsUrl(current.baseUrl),
                    "Bearer ${current.apiKey.trim()}"
                ).data.map { it.id.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        models = models,
                        model = when {
                            it.model.isBlank() && models.isNotEmpty() -> models.first()
                            it.model.isNotBlank() -> it.model
                            else -> SettingsRepository.DEFAULT_MODEL
                        },
                        isLoadingModels = false,
                        testMessage = if (models.isEmpty()) "没有拉取到模型，可继续手动填写" else "已拉取 ${models.size} 个模型",
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        testMessage = null,
                        error = "拉取模型失败：${e.message ?: e::class.java.simpleName}"
                    )
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.saveApiKey(_uiState.value.apiKey)
            settingsRepository.saveBaseUrl(_uiState.value.baseUrl)
            settingsRepository.saveModel(_uiState.value.model)
            _uiState.update { it.copy(isSaved = true, error = null) }
        }
    }

    fun testConnection() {
        val current = _uiState.value
        if (current.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先填写 API Key", testMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, error = null, testMessage = null) }
            runCatching {
                val request = AnalysisRequest(
                    model = current.model.ifBlank { SettingsRepository.DEFAULT_MODEL },
                    responseFormat = ResponseFormat(type = "json_object"),
                    messages = listOf(
                        Message(
                            role = "system",
                            content = "你是连接测试助手。必须只返回 JSON。"
                        ),
                        Message(
                            role = "user",
                            content = "请返回 {\"ok\":true}"
                        )
                    )
                )
                apiService.get().analyze(
                    buildDeepSeekChatUrl(current.baseUrl),
                    "Bearer ${current.apiKey.trim()}",
                    request
                )
            }.onSuccess { response ->
                val content = response.choices.firstOrNull()?.message?.content.orEmpty()
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testMessage = if (content.isBlank()) "连接成功" else "连接成功：$content",
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testMessage = null,
                        error = "连接失败：${e.message ?: e::class.java.simpleName}"
                    )
                }
            }
        }
    }
}
