package com.chen.memorizewords.data.study.local.room.model.study.progress.word

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordLearningStateDao {

    /**
     * 初始化一个新单词的学习状�?
     * 通常在第一次学该单词时调用
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialState(state: WordLearningStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: WordLearningStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<WordLearningStateEntity>)


    @Query(
        """
        SELECT * FROM word_learning_state
        WHERE word_id = :wordId and book_id = :bookId
    """
    )
    suspend fun getState(wordId: Long, bookId: Long): WordLearningStateEntity?

    @Query(
        """
    SELECT * FROM word_learning_state
    WHERE book_id = :bookId
    """
    )
    suspend fun getWordsByWordBookId(bookId: Long): List<WordLearningStateEntity>

    @Query("SELECT COUNT(*) FROM word_learning_state WHERE book_id = :bookId")
    suspend fun getWordCountByBookId(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM word_learning_state WHERE book_id = :bookId AND user_status = 0")
    suspend fun getLearnedCountByBookId(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM word_learning_state WHERE book_id = :bookId AND user_status = 1")
    suspend fun getMasteredCountByBookId(bookId: Long): Int

    @Query("SELECT COUNT(DISTINCT word_id) FROM word_learning_state")
    fun getStudyTotalWordCount(): Flow<Int>

    @Query(
        """
        DELETE FROM word_learning_state
        WHERE book_id = :bookId
        """
        )
    suspend fun deleteLearningWordByBookId(bookId: Long)

    @Query(
        """
        DELETE FROM word_learning_state
        WHERE book_id = :bookId AND word_id IN (:wordIds)
        """
    )
    suspend fun deleteByBookIdAndWordIds(bookId: Long, wordIds: List<Long>)

    /**
     * 批量读取指定 ids 的学习状态实�?
     *
     * @param ids 要查询的 wordId 列表（若为空，Room 通常会返回空列表，但调用者可先行检查）
     * @return 对应的实体列�?
     */
    @Query("SELECT * FROM word_learning_state WHERE word_id IN (:ids) AND book_id == :wordBookId")
    suspend fun getLearningStatesByIds(
        wordBookId: Long,
        ids: List<Long>
    ): List<WordLearningStateEntity>

    @Query(
        """
        SELECT word_id FROM word_learning_state
        WHERE book_id = :bookId
          AND user_status = 0
        ORDER BY next_review_time ASC, last_learn_time ASC, word_id ASC
        """
    )
    suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long>


    /**
     * 学习后更新状态（新学 / 复习通用�?
     *
     * 这里不直接写 SM-2 逻辑
     * �?Repository 计算�?nextReviewTime、masteryLevel 再传�?
     */
    /**
     * 更新单词学习状态（SM-2算法�?
     */
    @Query(
        """
        UPDATE word_learning_state
        SET total_learn_count = :totalLearnCount,
            last_learn_time = :learnTime,
            next_review_time = :nextReviewTime,
            mastery_level = :masteryLevel,
            user_status = :userStatus,
            repetition = :repetition,
            interval = :interval,
            efactor = :efactor
        WHERE word_id = :wordId AND book_id = :bookId
    """
    )
    suspend fun updateAfterLearn(
        bookId: Long,
        wordId: Long,
        totalLearnCount: Int,
        learnTime: Long,
        nextReviewTime: Long,
        masteryLevel: Int,
        userStatus: Int,
        repetition: Int,
        interval: Long,
        efactor: Double
    )

    @Query("DELETE FROM word_learning_state")
    suspend fun deleteAll()
}
