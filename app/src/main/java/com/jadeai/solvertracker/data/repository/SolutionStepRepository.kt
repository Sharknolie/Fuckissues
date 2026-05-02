package com.jadeai.solvertracker.data.repository

import com.jadeai.solvertracker.data.local.dao.SolutionStepDao
import com.jadeai.solvertracker.data.local.entity.SolutionStepEntity
import com.jadeai.solvertracker.domain.model.SolutionStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolutionStepRepository @Inject constructor(
    private val dao: SolutionStepDao
) {
    fun observeByTask(taskId: Long): Flow<List<SolutionStep>> = dao.observeByTask(taskId).map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getByTask(taskId: Long): List<SolutionStep> = dao.getByTask(taskId).map { it.toDomain() }

    suspend fun countByTask(taskId: Long): Int = dao.countByTask(taskId)

    suspend fun add(taskId: Long, problem: String, solution: String): Long {
        val nextOrder = (dao.maxOrder(taskId) ?: -1) + 1
        val entity = SolutionStepEntity(
            taskId = taskId,
            order = nextOrder,
            problem = problem.trim(),
            solution = solution.trim()
        )
        return dao.insert(entity)
    }

    suspend fun update(step: SolutionStep) {
        dao.update(
            SolutionStepEntity(
                id = step.id,
                taskId = step.taskId,
                order = step.order,
                problem = step.problem.trim(),
                solution = step.solution.trim(),
                createdAt = step.createdAt
            )
        )
    }

    suspend fun delete(stepId: Long) {
        dao.deleteById(stepId)
    }

    suspend fun reorder(taskId: Long, stepIds: List<Long>) {
        stepIds.forEachIndexed { index, id ->
            val entity = dao.getByTask(taskId).find { it.id == id } ?: return@forEachIndexed
            dao.update(entity.copy(order = index))
        }
    }

    private fun SolutionStepEntity.toDomain() = SolutionStep(
        id = id,
        taskId = taskId,
        order = order,
        problem = problem,
        solution = solution,
        createdAt = createdAt
    )
}
