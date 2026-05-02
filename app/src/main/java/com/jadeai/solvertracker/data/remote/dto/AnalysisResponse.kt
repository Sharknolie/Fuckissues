package com.jadeai.solvertracker.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChoiceMessage = ChoiceMessage()
)

@Serializable
data class ChoiceMessage(
    val role: String = "",
    val content: String = ""
)

@Serializable
data class AnalysisContent(
    val problemTypes: List<CategoryItemDto> = emptyList(),
    val solutionMethods: List<CategoryItemDto> = emptyList(),
    val summary: String = ""
)

@Serializable
data class CategoryItemDto(
    val type: String = "",
    val count: Int = 0,
    val steps: List<Int> = emptyList()
)
