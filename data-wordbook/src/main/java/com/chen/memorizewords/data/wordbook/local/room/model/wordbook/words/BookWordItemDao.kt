package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookWordItemDao {

    @Query("SELECT word_id FROM word_book_words WHERE word_book_id = :bookId")
    suspend fun getWordIdsForBook(bookId: Long): List<Long>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM word_book_words
            WHERE word_book_id = :bookId
              AND word_id = :wordId
        )
        """
    )
    suspend fun existsWordInBook(bookId: Long, wordId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(wordBookWords: WordBookItemEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(wordBookWords: List<WordBookItemEntity>)

    /**
     * 获取指定单词书中所有单词的数量
     * @param wordBookId 单词书 ID
     * @return 单词数量
     */
    @Query(
        """
        SELECT COUNT(*) FROM word_book_words
        WHERE word_book_id = :wordBookId
    """
    )
    suspend fun getWordCountByWordBookId(wordBookId: Long): Int

    @Query(
        """
        SELECT word_book_id AS bookId, COUNT(*) AS wordCount
        FROM word_book_words
        GROUP BY word_book_id
    """
    )
    fun observeBookWordCounts(): Flow<List<BookWordCount>>

    @Query("DELETE FROM word_book_words WHERE word_book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)

    @Query("DELETE FROM word_book_words WHERE word_book_id = :bookId AND word_id IN (:wordIds)")
    suspend fun deleteWordIds(bookId: Long, wordIds: List<Long>)

    @Query(
        """
        SELECT
            wbw.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            COALESCE((
                SELECT d.part_of_speech
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), 'other') AS partOfSpeech,
            COALESCE((
                SELECT d.meaning_chinese
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), '') AS meanings,
            COALESCE(wls.mastery_level, 0) AS masteryLevel,
            COALESCE(wls.total_learn_count, 0) AS totalLearnCount,
            COALESCE(wls.last_learn_time, 0) AS lastLearnTime,
            COALESCE(wls.next_review_time, 0) AS nextReviewTime,
            COALESCE(wls.user_status, 0) AS userStatus
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
        ORDER BY wbw.word_id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWordListRowsPageAll(bookId: Long, limit: Int, offset: Int): List<WordListRowProjection>

    @Query(
        """
        SELECT
            wbw.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            COALESCE((
                SELECT d.part_of_speech
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), 'other') AS partOfSpeech,
            COALESCE((
                SELECT d.meaning_chinese
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), '') AS meanings,
            COALESCE(wls.mastery_level, 0) AS masteryLevel,
            COALESCE(wls.total_learn_count, 0) AS totalLearnCount,
            COALESCE(wls.last_learn_time, 0) AS lastLearnTime,
            COALESCE(wls.next_review_time, 0) AS nextReviewTime,
            COALESCE(wls.user_status, 0) AS userStatus
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND COALESCE(wls.mastery_level, 0) >= :masteredLevel
        ORDER BY wbw.word_id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWordListRowsPageMastered(
        bookId: Long,
        limit: Int,
        offset: Int,
        masteredLevel: Int
    ): List<WordListRowProjection>

    @Query(
        """
        SELECT
            wbw.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            COALESCE((
                SELECT d.part_of_speech
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), 'other') AS partOfSpeech,
            COALESCE((
                SELECT d.meaning_chinese
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), '') AS meanings,
            COALESCE(wls.mastery_level, 0) AS masteryLevel,
            COALESCE(wls.total_learn_count, 0) AS totalLearnCount,
            COALESCE(wls.last_learn_time, 0) AS lastLearnTime,
            COALESCE(wls.next_review_time, 0) AS nextReviewTime,
            COALESCE(wls.user_status, 0) AS userStatus
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND COALESCE(wls.mastery_level, 0) > 0
          AND COALESCE(wls.mastery_level, 0) < :masteredLevel
        ORDER BY wbw.word_id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWordListRowsPageLearned(
        bookId: Long,
        limit: Int,
        offset: Int,
        masteredLevel: Int
    ): List<WordListRowProjection>

    @Query(
        """
        SELECT
            wbw.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            COALESCE((
                SELECT d.part_of_speech
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), 'other') AS partOfSpeech,
            COALESCE((
                SELECT d.meaning_chinese
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), '') AS meanings,
            COALESCE(wls.mastery_level, 0) AS masteryLevel,
            COALESCE(wls.total_learn_count, 0) AS totalLearnCount,
            COALESCE(wls.last_learn_time, 0) AS lastLearnTime,
            COALESCE(wls.next_review_time, 0) AS nextReviewTime,
            COALESCE(wls.user_status, 0) AS userStatus
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND COALESCE(wls.mastery_level, 0) <= 0
        ORDER BY wbw.word_id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWordListRowsPageToLearn(bookId: Long, limit: Int, offset: Int): List<WordListRowProjection>

    @Query(
        """
        SELECT
            wbw.word_id AS wordId,
            COALESCE(w.word, '') AS word,
            COALESCE(w.phonetic_us, w.phonetic_uk) AS phonetic,
            COALESCE((
                SELECT d.part_of_speech
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), 'other') AS partOfSpeech,
            COALESCE((
                SELECT d.meaning_chinese
                FROM word_definitions d
                WHERE d.word_id = wbw.word_id
                LIMIT 1
            ), '') AS meanings,
            COALESCE(wls.mastery_level, 0) AS masteryLevel,
            COALESCE(wls.total_learn_count, 0) AS totalLearnCount,
            COALESCE(wls.last_learn_time, 0) AS lastLearnTime,
            COALESCE(wls.next_review_time, 0) AS nextReviewTime,
            COALESCE(wls.user_status, 0) AS userStatus
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND (:favoriteOnly = 0 OR wbw.word_id IN (:favoriteWordIds))
          AND (
              :keyword = ''
              OR w.word LIKE '%' || :keyword || '%'
              OR EXISTS (
                  SELECT 1
                  FROM word_definitions d
                  WHERE d.word_id = wbw.word_id
                    AND d.meaning_chinese LIKE '%' || :keyword || '%'
              )
          )
          AND (
              :filter = 'ALL'
              OR (:filter = 'FAVORITE' AND wbw.word_id IN (:favoriteWordIds))
              OR (:filter = 'TO_LEARN' AND COALESCE(wls.total_learn_count, 0) = 0 AND COALESCE(wls.mastery_level, 0) <= 0)
              OR (:filter = 'LEARNED' AND COALESCE(wls.total_learn_count, 0) > 0 AND COALESCE(wls.mastery_level, 0) > 0 AND COALESCE(wls.mastery_level, 0) < :masteredLevel AND COALESCE(wls.user_status, 0) != 1)
              OR (:filter = 'MASTERED' AND (COALESCE(wls.mastery_level, 0) >= :masteredLevel OR COALESCE(wls.user_status, 0) = 1))
              OR (:filter = 'REVIEW_DUE' AND COALESCE(wls.total_learn_count, 0) > 0 AND COALESCE(wls.next_review_time, 0) > 0 AND COALESCE(wls.next_review_time, 0) <= :now AND COALESCE(wls.mastery_level, 0) < :masteredLevel AND COALESCE(wls.user_status, 0) != 1)
          )
        ORDER BY
            CASE WHEN :sortType = 'ALPHABETIC_ASC' THEN w.word END COLLATE NOCASE ASC,
            CASE WHEN :sortType = 'ALPHABETIC_DESC' THEN w.word END COLLATE NOCASE DESC,
            CASE WHEN :sortType = 'RECENT_LEARNED' THEN COALESCE(wls.last_learn_time, 0) END DESC,
            CASE WHEN :sortType = 'REVIEW_DUE_FIRST' THEN
                CASE
                    WHEN COALESCE(wls.total_learn_count, 0) > 0
                     AND COALESCE(wls.next_review_time, 0) > 0
                     AND COALESCE(wls.next_review_time, 0) <= :now
                     AND COALESCE(wls.mastery_level, 0) < :masteredLevel
                     AND COALESCE(wls.user_status, 0) != 1
                    THEN 1 ELSE 0
                END
            END DESC,
            CASE WHEN :sortType = 'REVIEW_DUE_FIRST' THEN COALESCE(wls.next_review_time, 9223372036854775807) END ASC,
            wbw.word_id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWordListRowsPage(
        bookId: Long,
        keyword: String,
        filter: String,
        sortType: String,
        favoriteOnly: Int,
        favoriteWordIds: List<Long>,
        masteredLevel: Int,
        now: Long,
        limit: Int,
        offset: Int
    ): List<WordListRowProjection>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words wbw
        LEFT JOIN words w ON w.id = wbw.word_id
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND (:favoriteOnly = 0 OR wbw.word_id IN (:favoriteWordIds))
          AND (
              :keyword = ''
              OR w.word LIKE '%' || :keyword || '%'
              OR EXISTS (
                  SELECT 1
                  FROM word_definitions d
                  WHERE d.word_id = wbw.word_id
                    AND d.meaning_chinese LIKE '%' || :keyword || '%'
              )
          )
          AND (
              :filter = 'ALL'
              OR (:filter = 'FAVORITE' AND wbw.word_id IN (:favoriteWordIds))
              OR (:filter = 'TO_LEARN' AND COALESCE(wls.total_learn_count, 0) = 0 AND COALESCE(wls.mastery_level, 0) <= 0)
              OR (:filter = 'LEARNED' AND COALESCE(wls.total_learn_count, 0) > 0 AND COALESCE(wls.mastery_level, 0) > 0 AND COALESCE(wls.mastery_level, 0) < :masteredLevel AND COALESCE(wls.user_status, 0) != 1)
              OR (:filter = 'MASTERED' AND (COALESCE(wls.mastery_level, 0) >= :masteredLevel OR COALESCE(wls.user_status, 0) = 1))
              OR (:filter = 'REVIEW_DUE' AND COALESCE(wls.total_learn_count, 0) > 0 AND COALESCE(wls.next_review_time, 0) > 0 AND COALESCE(wls.next_review_time, 0) <= :now AND COALESCE(wls.mastery_level, 0) < :masteredLevel AND COALESCE(wls.user_status, 0) != 1)
          )
        ORDER BY
            CASE WHEN :sortType = 'ALPHABETIC_ASC' THEN w.word END COLLATE NOCASE ASC,
            CASE WHEN :sortType = 'ALPHABETIC_DESC' THEN w.word END COLLATE NOCASE DESC,
            CASE WHEN :sortType = 'RECENT_LEARNED' THEN COALESCE(wls.last_learn_time, 0) END DESC,
            CASE WHEN :sortType = 'REVIEW_DUE_FIRST' THEN
                CASE
                    WHEN COALESCE(wls.total_learn_count, 0) > 0
                     AND COALESCE(wls.next_review_time, 0) > 0
                     AND COALESCE(wls.next_review_time, 0) <= :now
                     AND COALESCE(wls.mastery_level, 0) < :masteredLevel
                     AND COALESCE(wls.user_status, 0) != 1
                    THEN 1 ELSE 0
                END
            END DESC,
            CASE WHEN :sortType = 'REVIEW_DUE_FIRST' THEN COALESCE(wls.next_review_time, 9223372036854775807) END ASC,
            wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getWordListRowIds(
        bookId: Long,
        keyword: String,
        filter: String,
        sortType: String,
        favoriteOnly: Int,
        favoriteWordIds: List<Long>,
        masteredLevel: Int,
        now: Long,
        limit: Int
    ): List<Long>

    @Query(
        """
        SELECT
            COUNT(*) AS totalCount,
            SUM(CASE WHEN COALESCE(wls.total_learn_count, 0) > 0 THEN 1 ELSE 0 END) AS learnedCount,
            SUM(CASE WHEN COALESCE(wls.mastery_level, 0) >= :masteredLevel OR COALESCE(wls.user_status, 0) = 1 THEN 1 ELSE 0 END) AS masteredCount,
            SUM(CASE
                WHEN COALESCE(wls.total_learn_count, 0) > 0
                 AND COALESCE(wls.next_review_time, 0) > 0
                 AND COALESCE(wls.next_review_time, 0) <= :now
                 AND COALESCE(wls.mastery_level, 0) < :masteredLevel
                 AND COALESCE(wls.user_status, 0) != 1
                THEN 1 ELSE 0
            END) AS reviewDueCount,
            SUM(CASE WHEN wbw.word_id IN (:favoriteWordIds) THEN 1 ELSE 0 END) AS favoriteCount
        FROM word_book_words wbw
        LEFT JOIN word_learning_state wls
            ON wls.book_id = :bookId
            AND wls.word_id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
        """
    )
    suspend fun getWordListSummary(
        bookId: Long,
        favoriteWordIds: List<Long>,
        masteredLevel: Int,
        now: Long
    ): WordListSummaryProjection

    // 高性能：返回属于 bookId 且尚未有学习状态的 wordId 列表
    // 说明：通过子查询在 DB 层过滤掉已存在于 word_learning_state 表的 word
    @Query(
        """
        SELECT w.word_id
        FROM word_book_words AS w
        WHERE w.word_book_id = :bookId
          AND w.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
    """
    )
    suspend fun getUnlearnedWordIdsForBook(bookId: Long): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomUnlearnedWordIdsForBook(bookId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (:excludeIds)
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomUnlearnedWordIdsForBookExcluding(
        bookId: Long,
        limit: Int,
        excludeIds: List<Long>
    ): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY wrd.normalized_word ASC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsAlphabeticAsc(bookId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (:excludeIds)
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY wrd.normalized_word ASC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsAlphabeticAscExcluding(
        bookId: Long,
        limit: Int,
        excludeIds: List<Long>
    ): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY wrd.normalized_word DESC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsAlphabeticDesc(bookId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (:excludeIds)
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY wrd.normalized_word DESC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsAlphabeticDescExcluding(
        bookId: Long,
        limit: Int,
        excludeIds: List<Long>
    ): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY LENGTH(wrd.word) ASC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsLengthAsc(bookId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (:excludeIds)
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY LENGTH(wrd.word) ASC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsLengthAscExcluding(
        bookId: Long,
        limit: Int,
        excludeIds: List<Long>
    ): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY LENGTH(wrd.word) DESC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsLengthDesc(bookId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT wbw.word_id
        FROM word_book_words AS wbw
        LEFT JOIN words AS wrd ON wrd.id = wbw.word_id
        WHERE wbw.word_book_id = :bookId
          AND wbw.word_id NOT IN (:excludeIds)
          AND wbw.word_id NOT IN (
              SELECT word_id
              FROM word_learning_state
              WHERE book_id = :bookId
          )
        ORDER BY LENGTH(wrd.word) DESC, wbw.word_id ASC
        LIMIT :limit
        """
    )
    suspend fun getUnlearnedWordIdsLengthDescExcluding(
        bookId: Long,
        limit: Int,
        excludeIds: List<Long>
    ): List<Long>
}

data class BookWordCount(
    val bookId: Long,
    val wordCount: Int
)

data class WordListRowProjection(
    val wordId: Long,
    val word: String,
    val phonetic: String?,
    val partOfSpeech: String,
    val meanings: String,
    val masteryLevel: Int,
    val totalLearnCount: Int = 0,
    val lastLearnTime: Long = 0,
    val nextReviewTime: Long = 0,
    val userStatus: Int = 0
)

data class WordListSummaryProjection(
    val totalCount: Int,
    val learnedCount: Int?,
    val masteredCount: Int?,
    val reviewDueCount: Int?,
    val favoriteCount: Int?
)
