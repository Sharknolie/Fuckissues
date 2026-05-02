package com.jadeai.solvertracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jadeai.solvertracker.data.local.dao.AnalysisDao
import com.jadeai.solvertracker.data.local.dao.SolutionStepDao
import com.jadeai.solvertracker.data.local.dao.TaskDao
import com.jadeai.solvertracker.data.local.entity.AnalysisResultEntity
import com.jadeai.solvertracker.data.local.entity.SolutionStepEntity
import com.jadeai.solvertracker.data.local.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        SolutionStepEntity::class,
        AnalysisResultEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun solutionStepDao(): SolutionStepDao
    abstract fun analysisDao(): AnalysisDao
}
