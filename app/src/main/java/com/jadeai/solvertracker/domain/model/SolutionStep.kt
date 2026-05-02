package com.jadeai.solvertracker.domain.model

data class SolutionStep(
    val id: Long = 0,
    val taskId: Long,
    val order: Int,
    val problem: String,
    val solution: String,
    val createdAt: Long = System.currentTimeMillis()
)
