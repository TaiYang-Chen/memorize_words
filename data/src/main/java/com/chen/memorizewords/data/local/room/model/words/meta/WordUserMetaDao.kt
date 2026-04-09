package com.chen.memorizewords.data.local.room.model.words.meta

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordUserMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WordUserMetaEntity)

    @Query("DELETE FROM word_user_meta WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
