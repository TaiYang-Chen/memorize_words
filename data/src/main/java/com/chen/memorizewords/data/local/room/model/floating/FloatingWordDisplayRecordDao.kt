package com.chen.memorizewords.data.local.room.model.floating

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FloatingWordDisplayRecordDao {

    @Query("SELECT * FROM floating_word_display_record WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): FloatingWordDisplayRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FloatingWordDisplayRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FloatingWordDisplayRecordEntity>)

    @Query("SELECT COUNT(*) FROM floating_word_display_record")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM floating_word_display_record")
    suspend fun deleteAll()
}
