package com.chen.memorizewords.data.local.room.model.words.word

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface WordDao {

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<WordEntity>

    @Transaction
    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getWithRelationsByIds(ids: List<Long>): List<WordWithRelations>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: WordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Delete
    suspend fun delete(word: WordEntity)

    @Query("SELECT * FROM words WHERE id = :id")
    fun getWordById(id: Long): WordEntity?

    @Transaction
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordWithRelationsById(id: Long): WordWithRelations?

    @Transaction
    @Query("SELECT * FROM words WHERE word = :word")
    suspend fun getWordWithRelationsByWordString(word: String): WordWithRelations?

    @Transaction
    @Query("SELECT * FROM words WHERE normalized_word = :normalizedWord LIMIT 1")
    suspend fun getWordWithRelationsByNormalizedWord(normalizedWord: String): WordWithRelations?
}
