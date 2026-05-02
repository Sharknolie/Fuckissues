package com.jadeai.solvertracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.data.repository.SolutionStepRepository
import com.jadeai.solvertracker.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val DAYS_7_MS: Long = 7L * 24L * 60L * 60L * 1000L
private const val RANGE_TODAY = "today"
private const val RANGE_WEEK = "week"
private const val RANGE_MONTH = "month"

data class StatsDayUi(
    val label: String,
    val tasks: Int,
    val completedTasks: Int,
    val problems: Int
)

data class StatsRangeUi(
    val key: String,
    val completedTasks: Int,
    val tasks: Int,
    val totalProblems: Int,
    val averageProblems: Double,
    val days: List<StatsDayUi>
)

data class StatsUiState(
    val completedLast7Days: Int = 0,
    val tasksLast7Days: Int = 0,
    val totalProblemsLast7Days: Int = 0,
    val averageProblems: Double = 0.0,
    val ranges: List<StatsRangeUi> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val stepRepository: SolutionStepRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.observeAll().collect { tasks ->
                val now = System.currentTimeMillis()
                val since = now - DAYS_7_MS
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val stepCounts = tasks.associate { task ->
                    task.id to stepRepository.countByTask(task.id)
                }

                val last7 = tasks.filter { it.createdAt >= since }
                val totalProblems = last7.sumOf { stepCounts[it.id] ?: 0 }
                val completedCount = tasks.count {
                    it.status == TaskStatus.COMPLETED && (it.completedAt ?: 0L) >= since
                }

                val avg = if (last7.isNotEmpty()) {
                    totalProblems.toDouble() / last7.size.toDouble()
                } else {
                    0.0
                }

                _uiState.update {
                    it.copy(
                        completedLast7Days = completedCount,
                        tasksLast7Days = last7.size,
                        totalProblemsLast7Days = totalProblems,
                        averageProblems = avg,
                        ranges = listOf(
                            buildRange(RANGE_TODAY, 1, today, zoneId, tasks, stepCounts),
                            buildRange(RANGE_WEEK, 7, today, zoneId, tasks, stepCounts),
                            buildRange(RANGE_MONTH, 30, today, zoneId, tasks, stepCounts)
                        ),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun buildRange(
        key: String,
        dayCount: Int,
        today: LocalDate,
        zoneId: ZoneId,
        tasks: List<com.jadeai.solvertracker.domain.model.Task>,
        stepCounts: Map<Long, Int>
    ): StatsRangeUi {
        val startDate = today.minusDays(dayCount.toLong() - 1L)
        val formatter = DateTimeFormatter.ofPattern("MM-dd")
        val days = (0 until dayCount).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endMillis = date.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val tasksInDay = tasks.filter { task -> task.createdAt in startMillis until endMillis }
            val completedInDay = tasks.count { task ->
                task.status == TaskStatus.COMPLETED &&
                    (task.completedAt ?: 0L) in startMillis until endMillis
            }

            StatsDayUi(
                label = date.format(formatter),
                tasks = tasksInDay.size,
                completedTasks = completedInDay,
                problems = tasksInDay.sumOf { task -> stepCounts[task.id] ?: 0 }
            )
        }
        val totalTasks = days.sumOf { it.tasks }
        val totalProblems = days.sumOf { it.problems }

        return StatsRangeUi(
            key = key,
            completedTasks = days.sumOf { it.completedTasks },
            tasks = totalTasks,
            totalProblems = totalProblems,
            averageProblems = if (totalTasks == 0) 0.0 else totalProblems.toDouble() / totalTasks.toDouble(),
            days = days
        )
    }
}
