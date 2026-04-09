package com.chen.memorizewords.data.local.room.model.words.definition

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface WordDefinitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(definition: WordDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(definition: List<WordDefinitionEntity>)

    @Delete
    suspend fun delete(definition: WordDefinitionEntity)

    @Query("SELECT * FROM word_definitions WHERE word_id = :wordId")
    fun getDefinitionsForWordId(wordId: Long): Flow<List<WordDefinitionEntity>>

    @Query("DELETE FROM word_definitions WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    @Update
    suspend fun update(definition: WordDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordDefinitions(wordDefinitions: List<WordDefinitionEntity>)

    @Query(
        """
    SELECT * FROM word_definitions
    WHERE word_id = :wordId
"""
    )
    suspend fun getWordDefinitions(wordId: Long): List<WordDefinitionEntity>

    @Query(
        """
    SELECT * FROM word_definitions
    WHERE word_id = :wordId
    ORDER BY RANDOM()
    LIMIT 1
"""
    )
    suspend fun getOneWordDefinition(wordId: Long): WordDefinitionEntity?


    // 随机干扰项（排除当前 wordId）
    @Query(
        """
        SELECT * FROM word_definitions
        WHERE word_id != :wordId
        ORDER BY RANDOM()
        LIMIT :limit
    """
    )
    suspend fun getRandomWordDefinitionsExcept(wordId: Long, limit: Int): List<WordDefinitionEntity>

    @Query(
        """
        SELECT * FROM word_definitions 
        WHERE word_id = :wordId
        ORDER BY RANDOM()
        LIMIT 1
    """
    )
    suspend fun getRandomDefinition(wordId: Long): WordDefinitionEntity


    @Query(
        """
        SELECT * FROM word_definitions
        WHERE word_id != :wordId
        ORDER BY RANDOM()
        LIMIT :limit
    """
    )
    suspend fun getRandomDistractorsByPos(
        wordId: Long,
        limit: Int
    ): List<WordDefinitionEntity>
}
