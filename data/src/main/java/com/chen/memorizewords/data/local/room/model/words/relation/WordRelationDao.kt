package com.chen.memorizewords.data.local.room.model.words.relation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(items: List<WordSynonymEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAntonyms(items: List<WordAntonymEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(items: List<WordTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociations(items: List<WordAssociationEntity>)

    @Query("DELETE FROM word_synonyms WHERE word_id = :wordId")
    suspend fun deleteSynonymsByWordId(wordId: Long)

    @Query("DELETE FROM word_antonyms WHERE word_id = :wordId")
    suspend fun deleteAntonymsByWordId(wordId: Long)

    @Query("DELETE FROM word_tags WHERE word_id = :wordId")
    suspend fun deleteTagsByWordId(wordId: Long)

    @Query("DELETE FROM word_associations WHERE word_id = :wordId")
    suspend fun deleteAssociationsByWordId(wordId: Long)
}
