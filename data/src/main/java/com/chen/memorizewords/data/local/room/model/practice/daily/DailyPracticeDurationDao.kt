package com.chen.memorizewords.data.local.room.model.practice.daily

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPracticeDurationDao {

    @Query("SELECT * FROM daily_practice_duration WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyPracticeDurationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyPracticeDurationEntity>)

    @Query(
        """
        INSERT OR REPLACE INTO daily_practice_duration(
            date,
            total_duration_ms,
            updated_at
        )
        VALUES (
            :date,
            COALESCE((SELECT total_duration_ms FROM daily_practice_duration WHERE date = :date), 0) + :durationMs,
            :updatedAt
        )
        """
    )
    suspend fun addDuration(date: String, durationMs: Long, updatedAt: Long)

    @Query(
        """
        SELECT COALESCE(SUM(total_duration_ms), 0)
        FROM daily_practice_duration
        WHERE date = :date
        """
    )
    fun getTodayPracticeDurationMs(date: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(total_duration_ms), 0)
        FROM daily_practice_duration
        """
    )
    fun getPracticeTotalDurationMs(): Flow<Long>

    @Query(
        """
        SELECT
            date AS date,
            total_duration_ms AS durationMs
        FROM daily_practice_duration
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date
        """
    )
    fun getDailyPracticeDurationStats(
        startDate: String,
        endDate: String
    ): Flow<List<PracticeDailyDurationStatsProjection>>

    @Query(
        """
        SELECT date
        FROM daily_practice_duration
        WHERE total_duration_ms > 0
        ORDER BY date DESC
        """
    )
    fun getPracticeDatesDesc(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM daily_practice_duration")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM daily_practice_duration")
    suspend fun deleteAll()
}
