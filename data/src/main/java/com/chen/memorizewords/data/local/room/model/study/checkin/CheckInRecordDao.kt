package com.chen.memorizewords.data.local.room.model.study.checkin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CheckInRecordEntity>)

    @Query("SELECT * FROM check_in_record WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): CheckInRecordEntity?

    @Query("SELECT * FROM check_in_record WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<CheckInRecordEntity?>

    @Query("SELECT * FROM check_in_record ORDER BY date DESC")
    fun observeAllByDateDesc(): Flow<List<CheckInRecordEntity>>

    @Query("SELECT COUNT(*) FROM check_in_record")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM check_in_record")
    suspend fun deleteAll()
}
