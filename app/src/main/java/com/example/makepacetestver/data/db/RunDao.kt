package com.example.makepacetestver.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)

    @Query("SELECT * FROM running_table ORDER BY timestamp DESC")
    fun getAllRuns(): kotlinx.coroutines.flow.Flow<List<RunEntity>>
}
