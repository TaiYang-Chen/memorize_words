package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordLearningStateDao {

    /**
     * 鍒濆鍖栦竴涓柊鍗曡瘝鐨勫涔犵姸锟?
     * 閫氬父鍦ㄧ涓€娆″璇ュ崟璇嶆椂璋冪敤
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
     * 鎵归噺璇诲彇鎸囧畾 ids 鐨勫涔犵姸鎬佸疄锟?
     *
     * @param ids 瑕佹煡璇㈢殑 wordId 鍒楄〃锛堣嫢涓虹┖锛孯oom 閫氬父浼氳繑鍥炵┖鍒楄〃锛屼絾璋冪敤鑰呭彲鍏堣妫€鏌ワ級
     * @return 瀵瑰簲鐨勫疄浣撳垪锟?
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
        ORDER BY next_review_time ASC, last_learn_time ASC, word_id ASC
        """
    )
    suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long>


    /**
     * 瀛︿範鍚庢洿鏂扮姸鎬侊紙鏂板 / 澶嶄範閫氱敤锟?
     *
     * 杩欓噷涓嶇洿鎺ュ啓 SM-2 閫昏緫
     * 锟?Repository 璁＄畻锟?nextReviewTime銆乵asteryLevel 鍐嶄紶锟?
     */
    /**
     * 鏇存柊鍗曡瘝瀛︿範鐘舵€侊紙SM-2绠楁硶锟?
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
