package com.jadeai.solvertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jadeai.solvertracker.data.local.entity.AnalysisResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis_results WHERE taskId = :taskId")
    suspend fun getByTask(taskId: Long): AnalysisResultEntity?

    @Query("SELECT * FROM analysis_results WHERE taskId = :taskId")
    fun observeByTask(taskId: Long): Flow<AnalysisResultEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: AnalysisResultEntity): Long

    @Query("DELETE FROM analysis_results WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: Long)
}
