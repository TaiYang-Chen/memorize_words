package com.chen.memorizewords.data.local.room.model.study.daily

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordStudyRecordsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WordStudyRecordsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<WordStudyRecordsEntity>)

    @Query("SELECT COUNT(*) FROM word_study_records")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(DISTINCT date) FROM word_study_records")
    fun getStudyTotalDayCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date And is_new_word = 1")
    fun getTodayNewWordCount(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date And is_new_word = 0")
    fun getTodayReviewWordCount(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date And is_new_word = 1")
    suspend fun getTodayNewWordCountValue(date: String): Int

    @Query("SELECT COUNT(*) FROM word_study_records WHERE date = :date And is_new_word = 0")
    suspend fun getTodayReviewWordCountValue(date: String): Int

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
    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStatsProjection>>

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
    fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecordProjection>>

    @Query("DELETE FROM word_study_records")
    suspend fun deleteAll()

    @Query(
        """
        SELECT *
        FROM word_study_records
        ORDER BY date ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPaged(limit: Int, offset: Int): List<WordStudyRecordsEntity>
}
