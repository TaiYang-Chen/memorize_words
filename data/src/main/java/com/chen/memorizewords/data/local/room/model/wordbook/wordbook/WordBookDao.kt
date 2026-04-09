package com.chen.memorizewords.data.local.room.model.wordbook.wordbook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookDao {

    @Query("SELECT EXISTS(SELECT 1 FROM word_book WHERE id = :bookId)")
    suspend fun exists(bookId: Long): Boolean

    @Query("SELECT * FROM word_book")
    fun getAllWordBooks(): List<WordBookEntity>

    @Query("SELECT * FROM word_book")
    fun getAllWordBooksFlow(): Flow<List<WordBookEntity>>

    @Query("SELECT * FROM word_book")
    fun getMyWordBooksFlow(): Flow<List<WordBookEntity>>

    @Query("SELECT * FROM word_book WHERE id = :id")
    fun getWordBook(id: Long): WordBookEntity?

    @Query("SELECT * FROM word_book WHERE id = :id")
    suspend fun getWordBookById(id: Long): WordBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordBook(wordBook: WordBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordBooks(wordBooks: List<WordBookEntity>)

    @Query("SELECT name FROM word_book WHERE id = :bookId")
    suspend fun getBookNameById(bookId: Long): String?

    @Query("SELECT id FROM word_book")
    suspend fun getAllWordBookIds(): List<Long>

    @Query("DELETE FROM word_book WHERE id IN (:bookIds)")
    suspend fun deleteByIds(bookIds: List<Long>)

    @Query("DELETE FROM word_book")
    suspend fun deleteAll()

    @Transaction
    suspend fun setCurrentWordBook(bookId: Long) {
        deselectPreviousWordBook()
        selectWordBook(bookId)
    }

    @Query("UPDATE word_book SET is_selected = 0 WHERE is_selected = 1")
    suspend fun deselectPreviousWordBook()

    @Query("UPDATE word_book SET is_selected = 1 WHERE id = :bookId")
    suspend fun selectWordBook(bookId: Long)

    @Query("SELECT * FROM word_book WHERE is_selected == 1")
    fun getCurrentWordBookFlow(): Flow<WordBookEntity?>

    @Query("SELECT * FROM word_book WHERE is_selected == 1")
    suspend fun getCurrentWordBook(): WordBookEntity?

    @Query("SELECT word_id FROM word_book_words WHERE word_book_id = :wordBookId  LIMIT :limit OFFSET :offset")
    suspend fun getWordIdsPage(wordBookId: Long, limit: Int, offset: Int): List<Long>
}
