package com.jadeai.solvertracker.data.repository

import com.jadeai.solvertracker.data.local.dao.TaskDao
import com.jadeai.solvertracker.data.local.entity.TaskEntity
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    fun observeAll(): Flow<List<Task>> = taskDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getById(id: Long): Task? = taskDao.getById(id)?.toDomain()

    suspend fun create(title: String): Long {
        val entity = TaskEntity(title = title.trim())
        return taskDao.insert(entity)
    }

    suspend fun markCompleted(taskId: Long) {
        val entity = taskDao.getById(taskId) ?: return
        val updated = entity.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        taskDao.update(updated)
    }

    suspend fun updateTitle(taskId: Long, newTitle: String) {
        val entity = taskDao.getById(taskId) ?: return
        taskDao.update(entity.copy(title = newTitle.trim()))
    }

    suspend fun delete(taskId: Long) {
        taskDao.deleteById(taskId)
    }

    private fun TaskEntity.toDomain() = Task(
        id = id,
        title = title,
        createdAt = createdAt,
        completedAt = completedAt,
        status = status
    )
}
