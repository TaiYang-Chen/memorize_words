package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WordBookProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progressList: List<WordBookProgressEntity>)

    @Delete
    suspend fun delete(progress: WordBookProgressEntity)

    @Update
    suspend fun update(progress: WordBookProgressEntity)

    @Query("SELECT * FROM word_book_progress WHERE book_id = :bookId")
    suspend fun getProgress(bookId: Long): WordBookProgressEntity?

    @Query(
        """
        INSERT OR IGNORE INTO word_book_progress (
            book_id,
            correct_count,
            wrong_count,
            study_day_count,
            last_study_date,
            revision
        ) VALUES (
            :bookId,
            0,
            0,
            0,
            NULL,
            0
        )
        """
    )
    suspend fun ensureProgressRow(bookId: Long)

    @Query(
        """
        UPDATE word_book_progress
        SET
            correct_count = correct_count + CASE WHEN :isCorrect = 1 THEN 1 ELSE 0 END,
            wrong_count = wrong_count + CASE WHEN :isCorrect = 1 THEN 0 ELSE 1 END,
            study_day_count = CASE
                WHEN last_study_date = :today THEN study_day_count
                ELSE study_day_count + 1
            END,
            last_study_date = CASE
                WHEN last_study_date = :today THEN last_study_date
                ELSE :today
            END,
            revision = revision + 1
        WHERE book_id = :bookId
        """
    )
    suspend fun incrementAnswerStats(
        bookId: Long,
        isCorrect: Int,
        today: String
    )

    @Query("SELECT * FROM word_book_progress WHERE book_id IN (:bookIds)")
    fun getProgressesFlow(bookIds: List<Long>): Flow<List<WordBookProgressEntity>>

    @Query(
        """
        SELECT
            b.id AS wordBookId,
            COALESCE((
                SELECT COUNT(*)
                FROM word_learning_state
                WHERE book_id = b.id AND user_status = 0
            ), 0) AS learningCount,
            COALESCE((
                SELECT COUNT(*)
                FROM word_learning_state
                WHERE book_id = b.id AND user_status = 1
            ), 0) AS masteredCount,
            COALESCE(p.correct_count, 0) AS correctCount,
            COALESCE(p.wrong_count, 0) AS wrongCount,
            COALESCE(p.study_day_count, 0) AS studyDayCount,
            p.last_study_date AS lastStudyDate,
            COALESCE(p.revision, 0) AS revision
        FROM word_book b
        LEFT JOIN word_book_progress p ON p.book_id = b.id
        WHERE b.id = :bookId
        """
    )
    fun getWordBookProgress(bookId: Long): Flow<WordBookProgressSummary?>

    @Query(
        """
        SELECT
            b.id AS wordBookId,
            COALESCE((
                SELECT COUNT(*)
                FROM word_learning_state
                WHERE book_id = b.id AND user_status = 0
            ), 0) AS learningCount,
            COALESCE((
                SELECT COUNT(*)
                FROM word_learning_state
                WHERE book_id = b.id AND user_status = 1
            ), 0) AS masteredCount,
            COALESCE(p.correct_count, 0) AS correctCount,
            COALESCE(p.wrong_count, 0) AS wrongCount,
            COALESCE(p.study_day_count, 0) AS studyDayCount,
            p.last_study_date AS lastStudyDate,
            COALESCE(p.revision, 0) AS revision
        FROM word_book b
        LEFT JOIN word_book_progress p ON p.book_id = b.id
        WHERE b.id IN (:bookIds)
        """
    )
    fun getWordBooksProgress(bookIds: List<Long>): Flow<List<WordBookProgressSummary>>

    @Query("DELETE FROM word_book_progress")
    suspend fun deleteAll()

    @Query("DELETE FROM word_book_progress WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
