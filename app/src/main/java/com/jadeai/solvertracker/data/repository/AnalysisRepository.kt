package com.jadeai.solvertracker.data.repository

import com.jadeai.solvertracker.data.local.dao.AnalysisDao
import com.jadeai.solvertracker.data.local.dao.SolutionStepDao
import com.jadeai.solvertracker.data.local.entity.AnalysisResultEntity
import com.jadeai.solvertracker.data.local.entity.SolutionStepEntity
import com.jadeai.solvertracker.data.remote.DeepSeekApiService
import com.jadeai.solvertracker.data.remote.dto.AnalysisContent
import com.jadeai.solvertracker.data.remote.dto.AnalysisRequest
import com.jadeai.solvertracker.data.remote.dto.CategoryItemDto
import com.jadeai.solvertracker.data.remote.dto.Message
import com.jadeai.solvertracker.data.remote.dto.ResponseFormat
import com.jadeai.solvertracker.domain.model.AnalysisResult
import com.jadeai.solvertracker.domain.model.CategoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepository @Inject constructor(
    private val analysisDao: AnalysisDao,
    private val solutionStepDao: SolutionStepDao,
    private val settingsRepository: SettingsRepository,
    private val apiService: dagger.Lazy<DeepSeekApiService>
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun observeByTask(taskId: Long): Flow<AnalysisResult?> = analysisDao
        .observeByTask(taskId)
        .map { it?.toDomain() }

    suspend fun getCachedResult(taskId: Long): AnalysisResult? =
        analysisDao.getByTask(taskId)?.toDomain()

    suspend fun analyze(taskId: Long, taskTitle: String): AnalysisResult {
        val steps = solutionStepDao.getByTask(taskId)
        val apiKey = settingsRepository.getApiKey()
            ?: throw ApiKeyMissingException()

        val url = buildDeepSeekChatUrl(settingsRepository.getBaseUrl())
        val model = settingsRepository.getModel()
        val prompt = buildAnalysisPrompt(taskTitle, steps)
        val request = AnalysisRequest(
            model = model,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                Message(
                    role = "system",
                    content = "你是一个问题解决分析专家。分析用户的问题解决过程，返回JSON。" +
                        "用中文输出。JSON格式必须包含 problemTypes（数组，每项有type/count/steps）、" +
                        "solutionMethods（数组，每项有method/count/steps）、summary（字符串总结）。"
                ),
                Message(role = "user", content = prompt)
            )
        )

        val response = apiService.get().analyze(url, "Bearer $apiKey", request)
        val rawContent = response.choices.firstOrNull()?.message?.content
            ?: throw AnalysisException("AI 返回为空")

        val analysisContent = try {
            json.decodeFromString<AnalysisContent>(rawContent)
        } catch (e: Exception) {
            throw AnalysisException("解析 AI 返回失败: ${e.message}")
        }

        val entity = AnalysisResultEntity(
            taskId = taskId,
            problemTypes = encodeCategoryList(analysisContent.problemTypes),
            solutionMethods = encodeCategoryList(analysisContent.solutionMethods),
            summary = analysisContent.summary
        )
        analysisDao.insert(entity)

        return getCachedResult(taskId) ?: throw AnalysisException("保存分析结果失败")
    }

    private fun buildAnalysisPrompt(
        title: String,
        steps: List<SolutionStepEntity>
    ): String = buildString {
        append("任务: $title\n\n解决过程:\n")
        steps.forEach { step ->
            append("${step.order + 1}. 问题: ${step.problem}\n")
            append("   解决: ${step.solution}\n\n")
        }
        append("请分析以上过程中的问题类型和解决方法偏好。")
    }

    private fun encodeCategoryList(items: List<CategoryItemDto>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CategoryItemDto.serializer()),
            items
        )

    private fun AnalysisResultEntity.toDomain(): AnalysisResult {
        val problemTypes = try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CategoryItemDto.serializer()),
                problemTypes
            ).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }

        val solutionMethods = try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CategoryItemDto.serializer()),
                solutionMethods
            ).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }

        return AnalysisResult(
            id = id,
            taskId = taskId,
            problemTypes = problemTypes,
            solutionMethods = solutionMethods,
            summary = summary,
            analyzedAt = analyzedAt
        )
    }

    private fun CategoryItemDto.toDomain() = CategoryItem(
        type = type,
        count = count,
        steps = steps
    )
}

class ApiKeyMissingException : Exception("请先在设置页填入 API Key")
class AnalysisException(message: String) : Exception(message)
