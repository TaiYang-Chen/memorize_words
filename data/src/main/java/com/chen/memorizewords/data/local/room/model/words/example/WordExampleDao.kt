package com.chen.memorizewords.data.local.room.model.words.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordExampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(example: WordExampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(example: List<WordExampleEntity>)

    @Update
    suspend fun update(example: WordExampleEntity)

    @Delete
    suspend fun delete(example: WordExampleEntity)

    @Query("SELECT * FROM word_examples WHERE word_id = :wordId")
    suspend fun getExamplesByWordId(wordId: Long): List<WordExampleEntity>

    @Query("DELETE FROM word_examples WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
