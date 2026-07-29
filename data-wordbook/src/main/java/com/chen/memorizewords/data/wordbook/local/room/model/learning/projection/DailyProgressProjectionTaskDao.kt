package com.chen.memorizewords.data.wordbook.local.room.model.learning.projection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyProgressProjectionTaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: DailyProgressProjectionTaskEntity)

    @Query("SELECT * FROM daily_progress_projection_task WHERE client_event_id = :clientEventId")
    suspend fun getById(clientEventId: String): DailyProgressProjectionTaskEntity?

    @Query(
        """
        SELECT * FROM daily_progress_projection_task
        ORDER BY client_sequence ASC
        LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int): List<DailyProgressProjectionTaskEntity>

    @Query("DELETE FROM daily_progress_projection_task WHERE client_event_id = :clientEventId")
    suspend fun delete(clientEventId: String)
}
