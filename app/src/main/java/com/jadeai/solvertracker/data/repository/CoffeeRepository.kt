package com.jadeai.solvertracker.data.repository

import com.jadeai.solvertracker.data.remote.DeepSeekApiService
import com.jadeai.solvertracker.data.remote.dto.AnalysisRequest
import com.jadeai.solvertracker.data.remote.dto.ChatRequest
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseContent
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseItemDto
import com.jadeai.solvertracker.data.remote.dto.CoffeeContent
import com.jadeai.solvertracker.data.remote.dto.Message
import com.jadeai.solvertracker.data.remote.dto.ResponseFormat
import com.jadeai.solvertracker.domain.model.SolutionStep
import java.io.InterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiService: dagger.Lazy<DeepSeekApiService>
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun classifyProblems(problems: List<String>): CoffeeContent {
        val apiKey = settingsRepository.getApiKey() ?: throw ApiKeyMissingException()
        val url = buildDeepSeekChatUrl(settingsRepository.getBaseUrl())
        val model = settingsRepository.getModel()

        val request = AnalysisRequest(
            model = model,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                Message(
                    role = "system",
                    content = "你是一个温和但严谨的工程复盘助手。请把用户遇到的问题按类型聚类，必须只返回 JSON，包含 categories 和 summary。"
                ),
                Message(role = "user", content = buildPrompt(problems))
            )
        )

        val response = try {
            apiService.get().analyze(url, "Bearer $apiKey", request)
        } catch (e: InterruptedIOException) {
            throw AnalysisException("AI 响应超时，请稍后重试或减少这个任务的问题数量")
        }
        val rawContent = response.choices.firstOrNull()?.message?.content
            ?: throw AnalysisException("AI 返回为空")

        return try {
            json.decodeFromString(CoffeeContent.serializer(), rawContent)
        } catch (e: Exception) {
            throw AnalysisException("解析 AI 返回失败: ${e.message}")
        }
    }

    suspend fun analyzeTaskCauses(taskTitle: String, steps: List<SolutionStep>): CoffeeCauseContent {
        val apiKey = settingsRepository.getApiKey() ?: throw ApiKeyMissingException()
        val url = buildDeepSeekChatUrl(settingsRepository.getBaseUrl())
        val model = settingsRepository.getModel()

        val request = AnalysisRequest(
            model = model,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                Message(role = "system", content = buildCauseSystemPrompt()),
                Message(role = "user", content = buildCausePrompt(taskTitle, steps))
            )
        )

        val response = apiService.get().analyze(url, "Bearer $apiKey", request)
        val rawContent = response.choices.firstOrNull()?.message?.content
            ?: throw AnalysisException("AI 返回为空")

        return try {
            json.decodeFromString(CoffeeCauseContent.serializer(), rawContent)
        } catch (e: Exception) {
            throw AnalysisException("解析 AI 归因结果失败: ${e.message}")
        }
    }

    suspend fun discussCause(
        taskTitle: String,
        causeResult: CoffeeCauseContent?,
        selectedItem: CoffeeCauseItemDto?,
        messages: List<Message>
    ): String {
        val apiKey = settingsRepository.getApiKey() ?: throw ApiKeyMissingException()
        val url = buildDeepSeekChatUrl(settingsRepository.getBaseUrl())
        val model = settingsRepository.getModel()
        val request = ChatRequest(
            model = model,
            messages = listOf(
                Message(
                    role = "system",
                    content = buildChatSystemPrompt() + "\n\n" + buildChatContext(taskTitle, causeResult, selectedItem)
                )
            ) + messages
        )

        val response = try {
            apiService.get().chat(url, "Bearer $apiKey", request)
        } catch (e: InterruptedIOException) {
            throw AnalysisException("AI 响应超时，请稍后重试")
        }
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw AnalysisException("AI 返回为空")
    }

    suspend fun streamCauseDiscussion(
        taskTitle: String,
        causeResult: CoffeeCauseContent?,
        selectedItem: CoffeeCauseItemDto?,
        messages: List<Message>,
        onDelta: suspend (String) -> Unit
    ): String {
        val apiKey = settingsRepository.getApiKey() ?: throw ApiKeyMissingException()
        val url = buildDeepSeekChatUrl(settingsRepository.getBaseUrl())
        val model = settingsRepository.getModel()
        val request = ChatRequest(
            model = model,
            stream = true,
            messages = listOf(
                Message(
                    role = "system",
                    content = buildChatSystemPrompt() + "\n\n" + buildChatContext(taskTitle, causeResult, selectedItem)
                )
            ) + messages
        )

        val response = try {
            apiService.get().chatStream(url, "Bearer $apiKey", request)
        } catch (e: InterruptedIOException) {
            throw AnalysisException("AI 响应超时，请稍后重试")
        }
        val full = StringBuilder()
        try {
            withContext(Dispatchers.IO) {
                response.use { body ->
                    body.charStream().buffered().useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (!trimmed.startsWith("data:")) return@forEach
                            val data = trimmed.removePrefix("data:").trim()
                            if (data == "[DONE]") return@forEach
                            val delta = parseStreamDelta(data)
                            if (delta.isNotEmpty()) {
                                full.append(delta)
                                onDelta(delta)
                            }
                        }
                    }
                }
            }
        } catch (e: InterruptedIOException) {
            throw AnalysisException("AI 响应超时，请稍后重试")
        }
        return full.toString().trim().takeIf { it.isNotBlank() }
            ?: throw AnalysisException("AI 返回为空")
    }

    private fun parseStreamDelta(data: String): String = try {
        val choice = json.parseToJsonElement(data)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
        choice?.get("delta")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: choice?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
            ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun buildPrompt(problems: List<String>): String = buildString {
        append("以下是最近记录的问题原句列表，每行一条：\n")
        problems.take(200).forEachIndexed { index, problem ->
            append(index + 1)
            append(". ")
            append(problem.replace("\n", " ").trim())
            append("\n")
        }
        if (problems.size > 200) {
            append("后面还有 ")
            append(problems.size - 200)
            append(" 条已省略。\n")
        }
        append("\n请按问题类型聚类并统计次数。")
    }

    private fun buildCauseSystemPrompt(): String = """
        你是一个简洁、直接的任务复盘助手。用户会给你一个任务，以及这个任务下多条“问题+解决办法”。
        你的目标是只分析这一个任务，不要掺杂其它任务，不要写长篇解释。

        归因边界规则：
        1. factorType 只能是 external、internal、mixed。
        2. external（外界因素）：工具、平台、API、设备、网络、运行环境、第三方服务的硬性限制。
           例：Copilot 4 小时限额导致进度卡住，归为 external / 工具限制。
           不要发散到“用户没准备其他工具”“用户应提前规划”。
        3. internal（内在因素）：用户自己的判断、选择、策略、需求优先级、验证方式、信息收集不足。
           例：没有提前看文档导致走弯路，归为 internal / 信息收集不足。
        4. mixed：外界限制和用户策略问题同时存在，并且无法清楚拆分时才使用。
        5. 核心原则：直接归因，不发散；能归到外界硬限制的就归外界，不反推用户本可以怎样。
        6. normalizedProblem 必须是简短整理后的问题，只写问题本身，不写长篇推理。
        7. summary 必须是一句话，总结这个任务的主要归因。
        8. 不要返回 confidence、observedFacts、inferredCause、externalPart、internalPart、needsClarification、clarifyingQuestions、shortReason。

        必须只返回 JSON，结构如下：
        {
          "summary": "一句话总结这个任务的主要归因",
          "externalRatio": 60,
          "internalRatio": 30,
          "mixedRatio": 10,
          "items": [
            {
              "stepIndex": 1,
              "rawProblem": "原始问题",
              "rawSolution": "原始解决办法",
              "normalizedProblem": "简短整理后的问题",
              "factorType": "external",
              "factorCategory": "工具限制",
              "improvement": "下次改进建议"
            }
          ],
          "categories": [{"name":"工具限制","factorType":"external","count":2}],
          "advice": ["建议1"]
        }
    """.trimIndent()

    private fun buildCausePrompt(taskTitle: String, steps: List<SolutionStep>): String = buildString {
        append("任务标题: ")
        append(taskTitle.trim())
        append("\n\n这个任务下的问题与解决办法如下：\n")
        steps.forEachIndexed { index, step ->
            append(index + 1)
            append(". 问题: ")
            append(step.problem.replace("\n", " ").trim())
            append("\n   解决办法: ")
            append(step.solution.replace("\n", " ").trim())
            append("\n")
        }
        append("\n请只基于这个任务做外界因素/内在因素/混合因素归因分析。")
    }

    private fun buildChatSystemPrompt(): String = """
        你是一个温和、直接、简洁的任务复盘对话助手。
        只围绕当前任务和当前归因结果讨论，不要混入其他任务。
        必须优先、直接回答最后一条 user 消息；任务上下文只是背景资料，不是用户正在提问的内容。
        如果最后一条 user 消息和任务上下文冲突，以最后一条 user 消息为准。
        回答要短，优先给可执行建议；如果用户问题很宽泛，先指出最可能原因，再给下一步做法。
        区分外界因素和内在因素时，只看直接原因，不要过度反推用户本可以怎样。
        不要使用 Markdown 格式，不要使用星号加粗；如果需要列点，用普通短句或中文序号。
    """.trimIndent()

    private fun buildChatContext(
        taskTitle: String,
        causeResult: CoffeeCauseContent?,
        selectedItem: CoffeeCauseItemDto?
    ): String = buildString {
        append("当前任务：")
        append(taskTitle.ifBlank { "未命名任务" })
        append("\n")
        causeResult?.let { result ->
            append("AI归因总结：")
            append(result.summary)
            append("\n因素占比：外界")
            append(result.externalRatio)
            append("%，内在")
            append(result.internalRatio)
            append("%，混合")
            append(result.mixedRatio)
            append("%\n")
        }
        selectedItem?.let { item ->
            append("\n当前选中的单条问题：\n")
            append("序号：")
            append(item.stepIndex)
            append("\n原始问题：")
            append(item.rawProblem)
            append("\n解决办法：")
            append(item.rawSolution)
            append("\n整理后问题：")
            append(item.normalizedProblem)
            append("\n归因类型：")
            append(item.factorType)
            append("\n归因分类：")
            append(item.factorCategory)
            append("\n改进建议：")
            append(item.improvement)
            append("\n")
        }
        append("\n请基于以上上下文回答后续问题。")
    }
}
