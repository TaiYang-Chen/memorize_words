package com.chen.memorizewords.data.wordbook.local.room.model.learning.record

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordStudyRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WordStudyRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<WordStudyRecordEntity>)

    @Query("SELECT COUNT(DISTINCT date) FROM word_study_records")
    fun getStudyTotalDayCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date AND is_new_word = 1")
    fun getTodayNewWordCount(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date AND is_new_word = 0")
    fun getTodayReviewWordCount(date: String): Flow<Int>

    @Query(
        """
        SELECT
            date AS date,
            SUM(CASE WHEN is_new_word = 1 THEN 1 ELSE 0 END) AS newCount,
            SUM(CASE WHEN is_new_word = 0 THEN 1 ELSE 0 END) AS reviewCount
        FROM word_study_records
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date
        """
    )
    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<LearningDailyWordStatsProjection>>

    @Query(
        """
        SELECT
            word_id AS wordId,
            word AS word,
            definition AS definition,
            is_new_word AS isNewWord
        FROM word_study_records
        WHERE date = :date
        ORDER BY id DESC
        """
    )
    fun getDailyStudyWordRecords(date: String): Flow<List<LearningDailyStudyWordRecordProjection>>

    @Query("SELECT date FROM word_study_records WHERE date BETWEEN :startDate AND :endDate")
    fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date")
    fun observeRecordCountByDate(date: String): Flow<Int>

    @Query("DELETE FROM word_study_records")
    suspend fun deleteAll()
}
