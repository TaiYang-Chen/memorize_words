package com.chen.memorizewords.data.local.room.model.practice.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PracticeSessionRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PracticeSessionRecordEntity>)

    @Query(
        """
        SELECT *
        FROM practice_session_record
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY created_at DESC
        """
    )
    fun getRecentSessions(startDate: String, endDate: String): Flow<List<PracticeSessionRecordEntity>>

    @Query(
        """
        SELECT *
        FROM practice_session_record
        WHERE id = :recordId
        LIMIT 1
        """
    )
    suspend fun getSessionById(recordId: Long): PracticeSessionRecordEntity?

    @Query("SELECT COUNT(*) FROM practice_session_record")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM practice_session_record")
    suspend fun deleteAll()
}
