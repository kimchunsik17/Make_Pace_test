package com.example.makepacetestver.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunPoints(points: List<RunPointEntity>)

    @Query("SELECT * FROM running_table ORDER BY timestamp DESC")
    fun getAllRuns(): kotlinx.coroutines.flow.Flow<List<RunEntity>>

    @Query("SELECT * FROM running_table WHERE id = :runId")
    suspend fun getRunById(runId: Long): RunEntity?

    @Query("SELECT * FROM run_points WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getPointsForRun(runId: Long): List<RunPointEntity>
}
