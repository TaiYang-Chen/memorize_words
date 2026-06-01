package com.chen.memorizewords.data.floating.local.room.model.floating

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface FloatingWordDisplayRecordDao {

    @Transaction
    @Query("SELECT * FROM floating_word_display_record WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): FloatingWordDisplayRecordWithWords?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FloatingWordDisplayRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FloatingWordDisplayRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<FloatingWordDisplayWordEntity>)

    @Query("DELETE FROM floating_word_display_word WHERE record_date IN (:dates)")
    suspend fun deleteWordsByDates(dates: List<String>)

    @Query("SELECT COUNT(*) FROM floating_word_display_record")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM floating_word_display_record")
    suspend fun deleteAll()
}
