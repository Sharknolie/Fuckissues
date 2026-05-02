package com.jadeai.solvertracker.domain.model

data class AnalysisResult(
    val id: Long = 0,
    val taskId: Long,
    val problemTypes: List<CategoryItem>,
    val solutionMethods: List<CategoryItem>,
    val summary: String,
    val analyzedAt: Long = System.currentTimeMillis()
)

data class CategoryItem(
    val type: String,
    val count: Int,
    val steps: List<Int>
)
