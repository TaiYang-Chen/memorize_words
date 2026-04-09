package com.chen.memorizewords.data.local.room.model.wordbook.syncstate

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordBookSyncStateDao {

    @Query("SELECT * FROM word_book_sync_state")
    fun getAll(): List<WordBookSyncStateEntity>

    @Query("SELECT * FROM word_book_sync_state WHERE book_id = :bookId")
    fun getByBookId(bookId: Long): WordBookSyncStateEntity?

    @Query("SELECT * FROM word_book_sync_state WHERE book_id IN (:bookIds)")
    fun getByBookIds(bookIds: List<Long>): List<WordBookSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: WordBookSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entities: List<WordBookSyncStateEntity>)

    @Query("DELETE FROM word_book_sync_state WHERE book_id IN (:bookIds)")
    fun deleteByBookIds(bookIds: List<Long>)

    @Query("DELETE FROM word_book_sync_state")
    fun deleteAll()
}
