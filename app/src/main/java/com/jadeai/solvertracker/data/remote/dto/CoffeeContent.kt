package com.jadeai.solvertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CoffeeContent(
    val categories: List<CoffeeCategoryDto> = emptyList(),
    val summary: String = ""
)

@Serializable
data class CoffeeCategoryDto(
    val type: String = "",
    val count: Int = 0,
    val examples: List<String> = emptyList()
)

@Serializable
data class CoffeeCauseContent(
    val summary: String = "",
    val externalRatio: Int = 0,
    val internalRatio: Int = 0,
    val mixedRatio: Int = 0,
    val items: List<CoffeeCauseItemDto> = emptyList(),
    val categories: List<CoffeeCauseCategoryDto> = emptyList(),
    val advice: List<String> = emptyList()
)

@Serializable
data class CoffeeCauseItemDto(
    val stepIndex: Int = 0,
    val rawProblem: String = "",
    val rawSolution: String = "",
    val normalizedProblem: String = "",
    val factorType: String = "mixed",
    val factorCategory: String = "",
    val improvement: String = ""
)

@Serializable
data class CoffeeCauseCategoryDto(
    val name: String = "",
    val factorType: String = "mixed",
    val count: Int = 0
)
