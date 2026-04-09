package com.chen.memorizewords.data.local.room.model.study.daily

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStudyDurationDao {

    @Query("SELECT * FROM daily_study_duration WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStudyDurationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyStudyDurationEntity>)

    @Query("SELECT COUNT(*) FROM daily_study_duration")
    suspend fun getTotalCount(): Int

    @Query(
        """
        INSERT OR REPLACE INTO daily_study_duration(
            date,
            total_duration_ms,
            updated_at,
            is_new_plan_completed,
            is_review_plan_completed
        )
        VALUES (
            :date,
            COALESCE((SELECT total_duration_ms FROM daily_study_duration WHERE date = :date), 0) + :durationMs,
            :updatedAt,
            COALESCE((SELECT is_new_plan_completed FROM daily_study_duration WHERE date = :date), 0),
            COALESCE((SELECT is_review_plan_completed FROM daily_study_duration WHERE date = :date), 0)
        )
        """
    )
    suspend fun addDuration(date: String, durationMs: Long, updatedAt: Long)

    @Query(
        """
        INSERT OR REPLACE INTO daily_study_duration(
            date,
            total_duration_ms,
            updated_at,
            is_new_plan_completed,
            is_review_plan_completed
        )
        VALUES (
            :date,
            COALESCE((SELECT total_duration_ms FROM daily_study_duration WHERE date = :date), 0),
            :updatedAt,
            CASE
                WHEN COALESCE((SELECT is_new_plan_completed FROM daily_study_duration WHERE date = :date), 0) = 1 OR :isNewCompleted = 1
                    THEN 1
                ELSE 0
            END,
            CASE
                WHEN COALESCE((SELECT is_review_plan_completed FROM daily_study_duration WHERE date = :date), 0) = 1 OR :isReviewCompleted = 1
                    THEN 1
                ELSE 0
            END
        )
        """
    )
    suspend fun upsertPlanCompletion(
        date: String,
        isNewCompleted: Int,
        isReviewCompleted: Int,
        updatedAt: Long
    )

    @Query(
        """
        SELECT COALESCE(SUM(total_duration_ms), 0)
        FROM daily_study_duration
        WHERE date = :date
        """
    )
    fun getTodayStudyDurationMs(date: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(total_duration_ms), 0)
        FROM daily_study_duration
        """
    )
    fun getStudyTotalDurationMs(): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT date FROM word_study_records
            UNION
            SELECT date FROM daily_study_duration WHERE total_duration_ms > 0
        )
        """
    )
    fun getStudyTotalStudyDaysForStats(): Flow<Int>

    @Query(
        """
        SELECT date
        FROM daily_study_duration
        WHERE is_new_plan_completed = 1
        ORDER BY date DESC
        """
    )
    fun getNewPlanCompletedDatesDesc(): Flow<List<String>>

    @Query(
        """
        SELECT
            date AS date,
            total_duration_ms AS durationMs
        FROM daily_study_duration
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date
        """
    )
    fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStatsProjection>>

    @Query(
        """
        SELECT
            days.date AS date,
            CASE
                WHEN COALESCE(records.record_count, 0) > 0 OR COALESCE(duration.total_duration_ms, 0) > 0 THEN 1
                ELSE 0
            END AS hasStudy,
            CASE
                WHEN COALESCE(checkin.record_count, 0) > 0 THEN 1
                ELSE 0
            END AS hasCheckIn,
            COALESCE(duration.is_new_plan_completed, 0) AS isNewPlanCompleted,
            COALESCE(duration.is_review_plan_completed, 0) AS isReviewPlanCompleted
        FROM (
            SELECT date FROM word_study_records WHERE date BETWEEN :startDate AND :endDate
            UNION
            SELECT date FROM daily_study_duration WHERE date BETWEEN :startDate AND :endDate
            UNION
            SELECT date FROM check_in_record WHERE date BETWEEN :startDate AND :endDate
        ) AS days
        LEFT JOIN (
            SELECT date, COUNT(*) AS record_count
            FROM word_study_records
            WHERE date BETWEEN :startDate AND :endDate
            GROUP BY date
        ) AS records ON records.date = days.date
        LEFT JOIN (
            SELECT date, COUNT(*) AS record_count
            FROM check_in_record
            WHERE date BETWEEN :startDate AND :endDate
            GROUP BY date
        ) AS checkin ON checkin.date = days.date
        LEFT JOIN daily_study_duration AS duration ON duration.date = days.date
        ORDER BY days.date
        """
    )
    fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStatsProjection>>

    @Query(
        """
        SELECT
            total_duration_ms AS durationMs,
            is_new_plan_completed AS isNewPlanCompleted,
            is_review_plan_completed AS isReviewPlanCompleted
        FROM daily_study_duration
        WHERE date = :date
        LIMIT 1
        """
    )
    fun getDailyStudySummary(date: String): Flow<DailyStudySummaryProjection?>

    @Query("DELETE FROM daily_study_duration")
    suspend fun deleteAll()

    @Query(
        """
        SELECT *
        FROM daily_study_duration
        ORDER BY date ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPaged(limit: Int, offset: Int): List<DailyStudyDurationEntity>
}
