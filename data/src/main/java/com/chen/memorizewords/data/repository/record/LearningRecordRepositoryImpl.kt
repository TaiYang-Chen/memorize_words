package com.chen.memorizewords.data.repository.record

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInBusinessCalendar
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfig
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.local.mmkv.checkin.UNKNOWN_MAKEUP_CARD_BALANCE
import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.local.room.model.study.daily.CalendarDayStatsProjection
import com.chen.memorizewords.data.local.room.model.study.daily.DailyDurationStatsProjection
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudySummaryProjection
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyWordRecordProjection
import com.chen.memorizewords.data.local.room.model.study.daily.DailyWordStatsProjection
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.repository.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.data.repository.sync.DailyStudyDurationSyncPayload
import com.chen.memorizewords.data.repository.sync.StudyRecordSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.study.record.CalendarDayStats
import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.model.study.record.CheckInType
import com.chen.memorizewords.domain.model.study.record.DayCheckInDetail
import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.model.study.record.DailyStudySummary
import com.chen.memorizewords.domain.model.study.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import com.chen.memorizewords.domain.model.study.record.MakeUpCheckInException
import com.chen.memorizewords.domain.model.study.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class LearningRecordRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val wordStudyRecordsDao: WordStudyRecordsDao,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val studyPlanDataSource: StudyPlanDataSource,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : LearningRecordRepository {

    override fun getCurrentBusinessDate(): String {
        return checkInBusinessCalendar.currentBusinessDate()
    }

    override suspend fun addLearningRecord(
        word: Word,
        definition: String,
        isNewWord: Boolean
    ) {
        val today = checkInBusinessCalendar.currentBusinessDate()
        appDatabase.withTransaction {
            wordStudyRecordsDao.insert(
                WordStudyRecordsEntity(
                    date = today,
                    wordId = word.id,
                    word = word.word,
                    definition = definition,
                    isNewWord = isNewWord
                )
            )

            val plan = studyPlanDataSource.getStudyPlan()
            val todayNewCount = wordStudyRecordsDao.getTodayNewWordCountValue(today)
            val todayReviewCount = wordStudyRecordsDao.getTodayReviewWordCountValue(today)
            val dailyNewTarget = if (plan.dailyNewCount > 0) plan.dailyNewCount else 15
            val reviewTarget = if (plan.dailyReviewCount > 0) plan.dailyReviewCount else dailyNewTarget * 3
            val isNewDone = todayNewCount >= dailyNewTarget
            val isReviewDone = todayReviewCount >= reviewTarget
            dailyStudyDurationDao.upsertPlanCompletion(
                date = today,
                isNewCompleted = if (isNewDone) 1 else 0,
                isReviewCompleted = if (isReviewDone) 1 else 0,
                updatedAt = System.currentTimeMillis()
            )

            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.STUDY_RECORD,
                    bizKey = buildStudyRecordBizKey(today, word.id, isNewWord),
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(
                        StudyRecordSyncPayload(
                            date = today,
                            wordId = word.id,
                            word = word.word,
                            definition = definition,
                            isNewWord = isNewWord
                        )
                    )
                )
            )

            dailyStudyDurationDao.getByDate(today)?.let { duration ->
                syncOutboxDao.upsert(
                    syncOutboxEntity(
                        bizType = SyncOutboxBizType.DAILY_STUDY_DURATION,
                        bizKey = buildDailyStudyDurationBizKey(duration.date),
                        operation = SyncOutboxOperation.UPSERT,
                        payload = gson.toJson(
                            DailyStudyDurationSyncPayload(
                                date = duration.date,
                                totalDurationMs = duration.totalDurationMs,
                                updatedAt = duration.updatedAt,
                                isNewPlanCompleted = duration.isNewPlanCompleted,
                                isReviewPlanCompleted = duration.isReviewPlanCompleted
                            )
                        )
                    )
                )
            }
        }
        syncOutboxWorkScheduler.scheduleDrain()
    }

    override suspend fun addStudyDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        val date = checkInBusinessCalendar.currentBusinessDate()
        appDatabase.withTransaction {
            val updatedAt = System.currentTimeMillis()
            dailyStudyDurationDao.addDuration(
                date = date,
                durationMs = durationMs,
                updatedAt = updatedAt
            )

            dailyStudyDurationDao.getByDate(date)?.let { duration ->
                syncOutboxDao.upsert(
                    syncOutboxEntity(
                        bizType = SyncOutboxBizType.DAILY_STUDY_DURATION,
                        bizKey = buildDailyStudyDurationBizKey(duration.date),
                        operation = SyncOutboxOperation.UPSERT,
                        payload = gson.toJson(
                            DailyStudyDurationSyncPayload(
                                date = duration.date,
                                totalDurationMs = duration.totalDurationMs,
                                updatedAt = duration.updatedAt,
                                isNewPlanCompleted = duration.isNewPlanCompleted,
                                isReviewPlanCompleted = duration.isReviewPlanCompleted
                            )
                        )
                    )
                )
            }
        }
        syncOutboxWorkScheduler.scheduleDrain()
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
        return dailyStudyDurationDao.getCalendarDayStats(startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
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
                    syncOutboxDao.observeByBizType(SyncOutboxBizType.CHECKIN_RECORD)
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
            syncOutboxDao.getByBizType(SyncOutboxBizType.CHECKIN_RECORD)
        )
        val availableMakeupCardCount = resolveAvailableMakeupCardCount(config, pendingMakeupCount)
            ?: return Result.failure(MakeUpCheckInException.BalanceUnknown)
        if (availableMakeupCardCount <= 0) {
            return Result.failure(MakeUpCheckInException.NoAvailableCard)
        }

        val now = System.currentTimeMillis()
        val entity = CheckInRecordEntity(
            date = date,
            type = CheckInType.MAKEUP.name,
            signedAt = now,
            updatedAt = now
        )
        appDatabase.withTransaction {
            checkInRecordDao.upsert(entity)
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.CHECKIN_RECORD,
                    bizKey = buildCheckInRecordBizKey(date),
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(entity.toSyncPayload())
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
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
            type = CheckInType.AUTO.name,
            signedAt = now,
            updatedAt = now
        )
        appDatabase.withTransaction {
            checkInRecordDao.upsert(entity)
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.CHECKIN_RECORD,
                    bizKey = buildCheckInRecordBizKey(today),
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(entity.toSyncPayload())
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
        return Result.success(entity.toDomain())
    }

    private fun buildStudyRecordBizKey(date: String, wordId: Long, isNewWord: Boolean): String {
        return "study_record:$date:$wordId:$isNewWord"
    }

    private fun isAutoCheckInEligible(duration: DailyStudyDurationEntity?): Boolean {
        return isAutoCheckInEligibleForDuration(duration)
    }

    private fun buildDailyStudyDurationBizKey(date: String): String {
        return "daily_study_duration:$date"
    }

    private fun buildCheckInRecordBizKey(date: String): String {
        return "checkin_record:$date"
    }

    private fun countPendingMakeupCheckIns(outboxEntities: List<SyncOutboxEntity>): Int {
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

private fun DailyWordStatsProjection.toDomain(): DailyWordStats {
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

private fun DailyStudyWordRecordProjection.toDomain(): DailyStudyWordRecord {
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
        type = runCatching { CheckInType.valueOf(type) }.getOrDefault(CheckInType.AUTO),
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}

private fun CheckInRecordEntity.toSyncPayload(): CheckInRecordSyncPayload {
    return CheckInRecordSyncPayload(
        date = date,
        type = type,
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}
