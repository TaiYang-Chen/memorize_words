package com.chen.memorizewords.data.practice.repository

import androidx.room.withTransaction
import com.chen.memorizewords.data.practice.local.PracticeDatabase
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.data.practice.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.domain.sync.PracticeDurationSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.practice.PracticeRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.google.gson.Gson

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeRecordRepositoryImpl @Inject constructor(
    private val practiceDatabase: PracticeDatabase,
    private val dailyPracticeDurationDao: DailyPracticeDurationDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxWriter: SyncOutboxWriter,
    private val gson: Gson
) : PracticeRecordRepository {

    override suspend fun addPracticeDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        val today = checkInBusinessCalendar.currentBusinessDate()
        practiceDatabase.withTransaction {
            dailyPracticeDurationDao.addDuration(
                date = today,
                durationMs = durationMs,
                updatedAtMs = System.currentTimeMillis()
            )
            dailyPracticeDurationDao.getByDate(today)?.let { duration ->
                syncOutboxWriter.enqueueLatest(
                    bizType = OutboxTopic.PRACTICE_DURATION,
                    bizKey = "practice_duration:${duration.date}",
                    operation = SyncOperation.UPSERT,
                    payload = gson.toJson(
                        PracticeDurationSyncPayload(
                            date = duration.date,
                            totalDurationMs = duration.totalDurationMs,
                            updatedAtMs = duration.updatedAtMs
                        )
                    )
                )
            }
        }
    }

    override fun getTodayPracticeDurationMs(): Flow<Long> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                dailyPracticeDurationDao.getTodayPracticeDurationMs(
                    checkInBusinessCalendar.currentBusinessDate(config)
                )
            }
    }

    override fun getPracticeTotalDurationMs(): Flow<Long> {
        return dailyPracticeDurationDao.getPracticeTotalDurationMs()
    }

    override fun getContinuousPracticeDays(): Flow<Int> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                dailyPracticeDurationDao.getPracticeDatesDesc()
                    .map { dates -> checkInBusinessCalendar.calculateCurrentStreak(dates, config) }
            }
    }

    override fun getRecentPracticeDurationStats(
        dayCount: Int
    ): Flow<List<PracticeDailyDurationStats>> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                val dateRange = checkInBusinessCalendar.buildRecentDateRange(dayCount, config)
                dailyPracticeDurationDao.getDailyPracticeDurationStats(
                    startDate = dateRange.startDate,
                    endDate = dateRange.endDate
                ).map { list ->
                    val map = list.associateBy { it.date }
                    dateRange.dates.map { date ->
                        PracticeDailyDurationStats(
                            date = date,
                            durationMs = map[date]?.durationMs ?: 0L
                        )
                    }
                }
            }
    }
}
