package com.jadeai.solvertracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jadeai.solvertracker.data.local.entity.SolutionStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SolutionStepDao {
    @Query("SELECT * FROM solution_steps WHERE taskId = :taskId ORDER BY `order` ASC")
    fun observeByTask(taskId: Long): Flow<List<SolutionStepEntity>>

    @Query("SELECT * FROM solution_steps WHERE taskId = :taskId ORDER BY `order` ASC")
    suspend fun getByTask(taskId: Long): List<SolutionStepEntity>

    @Query("SELECT COUNT(*) FROM solution_steps WHERE taskId = :taskId")
    suspend fun countByTask(taskId: Long): Int

    @Query("SELECT MAX(`order`) FROM solution_steps WHERE taskId = :taskId")
    suspend fun maxOrder(taskId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: SolutionStepEntity): Long

    @Update
    suspend fun update(step: SolutionStepEntity)

    @Delete
    suspend fun delete(step: SolutionStepEntity)

    @Query("DELETE FROM solution_steps WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM solution_steps WHERE id = :id")
    suspend fun getById(id: Long): SolutionStepEntity?

    @Query("DELETE FROM solution_steps WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: Long)
}
