package com.jadeai.solvertracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.data.repository.CoffeeCauseCacheRepository
import com.jadeai.solvertracker.data.repository.CoffeeRepository
import com.jadeai.solvertracker.data.repository.SolutionStepRepository
import com.jadeai.solvertracker.data.repository.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class TaskCauseAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TaskCauseAnalysisWorkerEntryPoint::class.java
        )
        val taskRepository = entryPoint.taskRepository()
        val stepRepository = entryPoint.solutionStepRepository()
        val coffeeRepository = entryPoint.coffeeRepository()
        val cacheRepository = entryPoint.coffeeCauseCacheRepository()

        val task = taskRepository.getById(taskId) ?: return Result.failure()
        if (task.status != TaskStatus.COMPLETED) return Result.success()

        val steps = stepRepository.getByTask(taskId).filter { it.problem.isNotBlank() || it.solution.isNotBlank() }
        if (steps.isEmpty()) return Result.success()

        val signature = cacheRepository.signatureFor(steps)
        if (cacheRepository.get(taskId, signature) != null) return Result.success()

        return try {
            val result = coffeeRepository.analyzeTaskCauses(task.title, steps)
            cacheRepository.save(taskId, signature, result)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES && e.isRetryable()) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "AI 归因失败")))
            }
        }
    }

    private fun Exception.isRetryable(): Boolean =
        this !is com.jadeai.solvertracker.data.repository.ApiKeyMissingException || this is InterruptedIOException

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 3
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TaskCauseAnalysisWorkerEntryPoint {
    fun taskRepository(): TaskRepository
    fun solutionStepRepository(): SolutionStepRepository
    fun coffeeRepository(): CoffeeRepository
    fun coffeeCauseCacheRepository(): CoffeeCauseCacheRepository
}

@Singleton
class TaskCauseAnalysisWorkEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(taskId: Long) {
        val request = OneTimeWorkRequestBuilder<TaskCauseAnalysisWorker>()
            .setInputData(workDataOf(TaskCauseAnalysisWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                45,
                TimeUnit.SECONDS
            )
            .addTag(tagFor(taskId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueNameFor(taskId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun workInfos(taskId: Long) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(uniqueNameFor(taskId))

    companion object {
        fun uniqueNameFor(taskId: Long): String = "task_cause_analysis_$taskId"
        fun tagFor(taskId: Long): String = "task_cause_analysis_tag_$taskId"
    }
}
