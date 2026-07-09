package com.chen.memorizewords.data.study.repository.record

import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.core.common.calendar.CheckInConfig
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.common.calendar.UNKNOWN_MAKEUP_CARD_BALANCE
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.CalendarDayStatsProjection
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyDurationStatsProjection
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudySummaryProjection
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.LearningDailyStudyWordRecordProjection
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.LearningDailyWordStatsProjection
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao
import com.chen.memorizewords.data.study.repository.local.StudyRecordLocalStore
import com.chen.memorizewords.domain.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.OutboxRecord
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOutboxReader
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.model.record.MakeUpCheckInException
import com.chen.memorizewords.domain.study.model.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class LearningRecordRepositoryImpl @Inject constructor(
    private val wordStudyRecordsDao: WordStudyRecordDao,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxReader: SyncOutboxReader,
    private val studyRecordLocalStore: StudyRecordLocalStore,
    private val syncOutboxWriter: SyncOutboxWriter,
    private val gson: Gson
) : LearningRecordRepository {

    override fun getCurrentBusinessDate(): String {
        return checkInBusinessCalendar.currentBusinessDate()
    }

    override suspend fun addStudyDuration(durationMs: Long) {
        studyRecordLocalStore.addStudyDuration(durationMs)
        flushPendingStudyOutbox()
    }

    override fun getStudyTotalDayCount(): Flow<Int> {
        return wordStudyRecordsDao.getStudyTotalDayCount()
    }

    override fun getContinuousCheckInDays(): Flow<Int> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                checkInRecordDao.observeAllByDateDesc()
                    .map { records ->
                        checkInBusinessCalendar.calculateCurrentStreak(records.map { it.date }, config)
                    }
            }
    }

    override fun getTodayNewWordCount(): Flow<Int> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                wordStudyRecordsDao.getTodayNewWordCount(
                    checkInBusinessCalendar.currentBusinessDate(config)
                )
            }
    }

    override fun getTodayReviewWordCount(): Flow<Int> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                wordStudyRecordsDao.getTodayReviewWordCount(
                    checkInBusinessCalendar.currentBusinessDate(config)
                )
            }
    }

    override fun getTodayStudyDurationMs(): Flow<Long> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                dailyStudyDurationDao.getTodayStudyDurationMs(
                    checkInBusinessCalendar.currentBusinessDate(config)
                )
            }
    }

    override fun getStudyTotalDurationMs(): Flow<Long> {
        return dailyStudyDurationDao.getStudyTotalDurationMs()
    }

    override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> {
        return wordStudyRecordsDao.getDailyWordStats(startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getDailyDurationStats(
        startDate: String,
        endDate: String
    ): Flow<List<DailyDurationStats>> {
        return dailyStudyDurationDao.getDailyDurationStats(startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>> {
        return combine(
            wordStudyRecordsDao.observeStudyDatesBetween(startDate, endDate),
            dailyStudyDurationDao.getCalendarDayStats(startDate, endDate)
        ) { studyDates, calendarRows ->
            val studyDateSet = studyDates.toSet()
            val calendarByDate = calendarRows.associateBy { it.date }
            (studyDateSet + calendarByDate.keys)
                .sorted()
                .map { date ->
                    calendarByDate[date]
                        ?.toDomain()
                        ?.copy(hasStudy = date in studyDateSet || calendarByDate[date]?.hasStudy == true)
                        ?: CalendarDayStats(
                            date = date,
                            hasStudy = true,
                            hasCheckIn = false,
                            isNewPlanCompleted = false,
                            isReviewPlanCompleted = false
                        )
                }
        }
    }

    override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> {
        return wordStudyRecordsDao.getDailyStudyWordRecords(date)
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getDailyStudySummary(date: String): Flow<DailyStudySummary> {
        return dailyStudyDurationDao.getDailyStudySummary(date)
            .map { projection ->
                projection?.toDomain(date) ?: DailyStudySummary(
                    date = date,
                    durationMs = 0L,
                    isNewPlanCompleted = false,
                    isReviewPlanCompleted = false
                )
            }
    }

    override fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                combine(
                    checkInRecordDao.observeByDate(date),
                    syncOutboxReader.observeByTopic(OutboxTopic.CHECKIN_RECORD)
                ) { record, pendingOutbox ->
                    val pendingMakeupCount = countPendingMakeupCheckIns(pendingOutbox)
                    val availableMakeupCardCount = resolveAvailableMakeupCardCount(
                        config = config,
                        pendingMakeupCount = pendingMakeupCount
                    )
                    val today = checkInBusinessCalendar.currentBusinessDate(config)
                    DayCheckInDetail(
                        date = date,
                        record = record?.toDomain(),
                        canMakeUp = record == null &&
                            date < today,
                        availableMakeupCardCount = availableMakeupCardCount
                    )
                }
            }
    }

    override suspend fun getTodayCheckInEntryState(): TodayCheckInEntryState {
        val today = checkInBusinessCalendar.currentBusinessDate()
        return TodayCheckInEntryState(
            businessDate = today,
            eligible = isAutoCheckInEligible(dailyStudyDurationDao.getByDate(today)),
            alreadyCheckedIn = checkInRecordDao.getByDate(today) != null
        )
    }

    override suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> {
        val config = checkInBusinessCalendar.resolvedConfig()
        val today = checkInBusinessCalendar.currentBusinessDate(config)
        if (date >= today) {
            return Result.failure(MakeUpCheckInException.FutureDate)
        }

        checkInRecordDao.getByDate(date)?.let { existing ->
            return Result.success(existing.toDomain())
        }

        val pendingMakeupCount = countPendingMakeupCheckIns(
            syncOutboxReader.getByTopic(OutboxTopic.CHECKIN_RECORD)
        )
        val availableMakeupCardCount = resolveAvailableMakeupCardCount(config, pendingMakeupCount)
            ?: return Result.failure(MakeUpCheckInException.BalanceUnknown)
        if (availableMakeupCardCount <= 0) {
            return Result.failure(MakeUpCheckInException.NoAvailableCard)
        }

        val now = System.currentTimeMillis()
        val entity = CheckInRecordEntity(
            date = date,
            type = CheckInType.MAKEUP,
            signedAtMs = now,
            updatedAtMs = now
        )
        studyRecordLocalStore.upsertCheckInRecord(entity)
        flushPendingStudyOutbox()
        return Result.success(entity.toDomain())
    }

    override suspend fun autoCheckInTodayIfEligible(): Result<CheckInRecord?> {
        val today = checkInBusinessCalendar.currentBusinessDate()
        checkInRecordDao.getByDate(today)?.let { existing ->
            return Result.success(existing.toDomain())
        }

        val isEligible = isAutoCheckInEligible(dailyStudyDurationDao.getByDate(today))
        if (!isEligible) {
            return Result.success(null)
        }

        val now = System.currentTimeMillis()
        val entity = CheckInRecordEntity(
            date = today,
            type = CheckInType.AUTO,
            signedAtMs = now,
            updatedAtMs = now
        )
        studyRecordLocalStore.upsertCheckInRecord(entity)
        flushPendingStudyOutbox()
        return Result.success(entity.toDomain())
    }

    private suspend fun flushPendingStudyOutbox() {
        flushPendingOutboxCommands(
            loadCommands = studyRecordLocalStore::getPendingOutboxCommands,
            enqueueCommands = syncOutboxWriter::enqueueLatest,
            deleteCommands = studyRecordLocalStore::deletePendingOutboxCommands
        )
    }

    private fun isAutoCheckInEligible(duration: DailyStudyDurationEntity?): Boolean {
        return isAutoCheckInEligibleForDuration(duration)
    }


    private fun countPendingMakeupCheckIns(outboxEntities: List<OutboxRecord>): Int {
        return outboxEntities.count { entity ->
            runCatching {
                gson.fromJson(entity.payload, CheckInRecordSyncPayload::class.java).type ==
                    CheckInType.MAKEUP.name
            }.getOrDefault(false)
        }
    }

    private fun resolveAvailableMakeupCardCount(
        config: CheckInConfig,
        pendingMakeupCount: Int
    ): Int? {
        if (config.cachedMakeupCardBalance == UNKNOWN_MAKEUP_CARD_BALANCE) {
            return null
        }
        return (config.cachedMakeupCardBalance - pendingMakeupCount).coerceAtLeast(0)
    }
}

internal fun isAutoCheckInEligibleForDuration(duration: DailyStudyDurationEntity?): Boolean {
    return duration?.let { it.isNewPlanCompleted || it.isReviewPlanCompleted } == true
}

private fun LearningDailyWordStatsProjection.toDomain(): DailyWordStats {
    return DailyWordStats(
        date = date,
        newCount = newCount,
        reviewCount = reviewCount
    )
}

private fun DailyDurationStatsProjection.toDomain(): DailyDurationStats {
    return DailyDurationStats(
        date = date,
        durationMs = durationMs
    )
}

private fun CalendarDayStatsProjection.toDomain(): CalendarDayStats {
    return CalendarDayStats(
        date = date,
        hasStudy = hasStudy,
        hasCheckIn = hasCheckIn,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private fun LearningDailyStudyWordRecordProjection.toDomain(): DailyStudyWordRecord {
    return DailyStudyWordRecord(
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}

private fun DailyStudySummaryProjection.toDomain(date: String): DailyStudySummary {
    return DailyStudySummary(
        date = date,
        durationMs = durationMs,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private fun CheckInRecordEntity.toDomain(): CheckInRecord {
    return CheckInRecord(
        date = date,
        type = type,
        signedAtMs = signedAtMs,
        updatedAtMs = updatedAtMs
    )
}

internal suspend fun flushPendingOutboxCommands(
    loadCommands: suspend () -> List<OutboxCommand>,
    enqueueCommands: suspend (List<OutboxCommand>) -> Unit,
    deleteCommands: suspend (List<OutboxCommand>) -> Unit
): Boolean {
    val commands = loadCommands()
    if (commands.isEmpty()) return true
    return try {
        enqueueCommands(commands)
        deleteCommands(commands)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
