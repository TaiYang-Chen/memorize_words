package com.chen.memorizewords.data.practice.local.room.model.practice.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PracticeSessionRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PracticeSessionRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<PracticeSessionWordEntity>)

    @Query("DELETE FROM practice_session_word WHERE session_id IN (:sessionIds)")
    suspend fun deleteWordsBySessionIds(sessionIds: List<Long>)

    @Transaction
    @Query(
        """
        SELECT *
        FROM practice_session_record
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY created_at DESC
        """
    )
    fun getRecentSessions(startDate: String, endDate: String): Flow<List<PracticeSessionRecordWithWords>>

    @Transaction
    @Query(
        """
        SELECT *
        FROM practice_session_record
        WHERE id = :recordId
        LIMIT 1
        """
    )
    suspend fun getSessionById(recordId: Long): PracticeSessionRecordWithWords?

    @Query("SELECT COUNT(*) FROM practice_session_record")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM practice_session_record")
    suspend fun deleteAll()
}
