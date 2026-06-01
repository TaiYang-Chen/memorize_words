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

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(wordBookWords: WordBookItemEntity)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(wordBookWords: List<WordBookItemEntity>)

    /**
     * 鑾峰彇鎸囧畾鍗曡瘝鏈腑鎵€鏈夊崟璇嶇殑鏁伴噺
     * @param wordBookId 鍗曡瘝鏈琁D
     * @return 鍗曡瘝鏁伴噺
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
            COALESCE(wls.mastery_level, 0) AS masteryLevel
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
            COALESCE(wls.mastery_level, 0) AS masteryLevel
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
            COALESCE(wls.mastery_level, 0) AS masteryLevel
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
            COALESCE(wls.mastery_level, 0) AS masteryLevel
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

    // 楂樻€ц兘锛氳繑鍥炲睘锟?bookId 涓斿皻鏈湁瀛︿範鐘舵€佺殑 wordId 鍒楄〃
    // 瑙ｉ噴锛氶€氳繃瀛愭煡璇㈠湪 DB 灞傝繃婊ゆ帀宸插湪 word_learning_state 琛ㄥ瓨鍦ㄧ殑 word
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
    val masteryLevel: Int
)
