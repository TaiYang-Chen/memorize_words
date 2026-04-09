package com.chen.memorizewords.data.local.room.model.words.form

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordFormDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(form: WordFormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(form: List<WordFormEntity>)

    @Update
    suspend fun update(form: WordFormEntity)

    @Delete
    suspend fun delete(form: WordFormEntity)

    @Query("SELECT * FROM word_forms WHERE word_id = :wordId")
    fun getFormsByWordId(wordId: Long): List<WordFormEntity>

    @Query("SELECT * FROM word_forms WHERE LOWER(form_text) = LOWER(:formText)")
    fun getByFormText(formText: String): List<WordFormEntity>

    @Query("DELETE FROM word_forms WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
