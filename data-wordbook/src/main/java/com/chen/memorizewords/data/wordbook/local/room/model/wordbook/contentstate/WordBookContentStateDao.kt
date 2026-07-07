package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookContentStateDao {
    @Query("SELECT * FROM word_book_content_state WHERE book_id = :bookId")
    suspend fun get(bookId: Long): WordBookContentStateEntity?

    @Query("SELECT * FROM word_book_content_state WHERE book_id = :bookId")
    fun observe(bookId: Long): Flow<WordBookContentStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordBookContentStateEntity)
}
