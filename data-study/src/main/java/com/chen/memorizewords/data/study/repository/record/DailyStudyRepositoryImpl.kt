package com.chen.memorizewords.data.study.repository.record

import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.core.common.calendar.CheckInConfig
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.common.calendar.UNKNOWN_MAKEUP_CARD_BALANCE
import com.chen.memorizewords.core.common.time.AppClock
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.CalendarDayStatsProjection
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyDurationStatsProjection
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudySummaryProjection
import com.chen.memorizewords.data.study.repository.local.StudyRecordLocalStore
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.MakeUpCheckInException
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import com.chen.memorizewords.domain.study.repository.sync.DailyStudySyncSnapshot
import com.chen.memorizewords.domain.study.repository.sync.StudySyncPort
import com.chen.memorizewords.domain.sync.PendingCheckInSyncQuery
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class DailyStudyRepositoryImpl @Inject constructor(
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val studyRecordLocalStore: StudyRecordLocalStore,
    private val pendingCheckInSyncQuery: PendingCheckInSyncQuery,
    private val studySyncPort: StudySyncPort,
    private val clock: AppClock
) : DailyStudyRepository {

    override suspend fun addStudyDuration(durationMs: Long) {
        val snapshot = studyRecordLocalStore.addStudyDuration(durationMs).dailyStudyDuration ?: return
        studySyncPort.scheduleDailyStudy(snapshot.toSyncSnapshot())
    }

    override fun getContinuousCheckInDays(): Flow<Int> =
        checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                checkInRecordDao.observeAllByDateDesc().map { records ->
                    checkInBusinessCalendar.calculateCurrentStreak(records.map { it.date }, config)
                }
            }

    override fun getStudyDuration(date: String): Flow<Long> =
        dailyStudyDurationDao.getTodayStudyDurationMs(date)

    override fun getStudyTotalDurationMs(): Flow<Long> =
        dailyStudyDurationDao.getStudyTotalDurationMs()

    override fun getDailyDurationStats(
        startDate: String,
        endDate: String
    ): Flow<List<DailyDurationStats>> =
        dailyStudyDurationDao.getDailyDurationStats(startDate, endDate)
            .map { rows -> rows.map(DailyDurationStatsProjection::toDomain) }

    override fun getDailyCalendarStats(
        startDate: String,
        endDate: String
    ): Flow<List<CalendarDayStats>> =
        dailyStudyDurationDao.getCalendarDayStats(startDate, endDate)
            .map { rows -> rows.map(CalendarDayStatsProjection::toDomain) }

    override fun getDailyStudySummary(date: String): Flow<DailyStudySummary> =
        dailyStudyDurationDao.getDailyStudySummary(date).map { row ->
            row?.toDomain(date) ?: DailyStudySummary(
                date = date,
                durationMs = 0L,
                isNewPlanCompleted = false,
                isReviewPlanCompleted = false
            )
        }

    override fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> =
        checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                combine(
                    checkInRecordDao.observeByDate(date),
                    pendingCheckInSyncQuery.observePendingMakeupCheckInCount()
                ) { record, pendingMakeupCount ->
                    val today = checkInBusinessCalendar.currentBusinessDate(config)
                    DayCheckInDetail(
                        date = date,
                        record = record?.toDomain(),
                        canMakeUp = record == null && date < today,
                        availableMakeupCardCount = resolveAvailableMakeupCardCount(
                            config,
                            pendingMakeupCount
                        )
                    )
                }
            }

    override fun observeCheckInRecord(date: String): Flow<CheckInRecord?> =
        checkInRecordDao.observeByDate(date).map { it?.toDomain() }

    override suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> {
        val config = checkInBusinessCalendar.resolvedConfig()
        val today = checkInBusinessCalendar.currentBusinessDate(config)
        if (date >= today) return Result.failure(MakeUpCheckInException.FutureDate)

        checkInRecordDao.getByDate(date)?.let { return Result.success(it.toDomain()) }
        val pendingCount = pendingCheckInSyncQuery.countPendingMakeupCheckIns()
        val available = resolveAvailableMakeupCardCount(config, pendingCount)
            ?: return Result.failure(MakeUpCheckInException.BalanceUnknown)
        if (available <= 0) return Result.failure(MakeUpCheckInException.NoAvailableCard)

        val now = clock.nowEpochMillis()
        val candidate = CheckInRecordEntity(
            date = date,
            type = CheckInType.MAKEUP,
            signedAtMs = now,
            updatedAtMs = now
        )
        val committed = studyRecordLocalStore.upsertCheckInRecord(candidate).checkInRecord ?: candidate
        val record = committed.toDomain()
        studySyncPort.scheduleCheckIn(record) {
            checkInConfigDataSource.consumeCachedMakeupCardBalance()
        }
        return Result.success(record)
    }

    private fun resolveAvailableMakeupCardCount(
        config: CheckInConfig,
        pendingMakeupCount: Int
    ): Int? {
        if (config.cachedMakeupCardBalance == UNKNOWN_MAKEUP_CARD_BALANCE) return null
        return (config.cachedMakeupCardBalance - pendingMakeupCount).coerceAtLeast(0)
    }
}

private fun DailyDurationStatsProjection.toDomain(): DailyDurationStats =
    DailyDurationStats(date = date, durationMs = durationMs)

private fun CalendarDayStatsProjection.toDomain(): CalendarDayStats = CalendarDayStats(
    date = date,
    hasStudy = hasStudy,
    hasCheckIn = hasCheckIn,
    isNewPlanCompleted = isNewPlanCompleted,
    isReviewPlanCompleted = isReviewPlanCompleted
)

private fun DailyStudySummaryProjection.toDomain(date: String): DailyStudySummary =
    DailyStudySummary(
        date = date,
        durationMs = durationMs,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )

private fun CheckInRecordEntity.toDomain(): CheckInRecord = CheckInRecord(
    date = date,
    type = type,
    signedAtMs = signedAtMs,
    updatedAtMs = updatedAtMs
)

private fun com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity.toSyncSnapshot() =
    DailyStudySyncSnapshot(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAtMs = updatedAtMs,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
