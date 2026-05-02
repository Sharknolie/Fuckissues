package com.jadeai.solvertracker.domain.model

import com.jadeai.solvertracker.data.local.entity.TaskStatus

data class Task(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: String = TaskStatus.IN_PROGRESS,
    val stepCount: Int = 0
)
