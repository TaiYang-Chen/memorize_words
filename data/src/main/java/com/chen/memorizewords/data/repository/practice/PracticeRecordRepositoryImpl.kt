package com.chen.memorizewords.data.repository.practice

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInBusinessCalendar
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.PracticeDurationSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.repository.practice.PracticeRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.google.gson.Gson

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeRecordRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val dailyPracticeDurationDao: DailyPracticeDurationDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : PracticeRecordRepository {

    override suspend fun addPracticeDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        val today = checkInBusinessCalendar.currentBusinessDate()
        appDatabase.withTransaction {
            dailyPracticeDurationDao.addDuration(
                date = today,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
            dailyPracticeDurationDao.getByDate(today)?.let { duration ->
                syncOutboxDao.upsert(
                    syncOutboxEntity(
                        bizType = SyncOutboxBizType.PRACTICE_DURATION,
                        bizKey = "practice_duration:${duration.date}",
                        operation = SyncOutboxOperation.UPSERT,
                        payload = gson.toJson(
                            PracticeDurationSyncPayload(
                                date = duration.date,
                                totalDurationMs = duration.totalDurationMs,
                                updatedAt = duration.updatedAt
                            )
                        )
                    )
                )
            }
        }
        syncOutboxWorkScheduler.scheduleDrain()
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
