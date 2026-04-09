package com.chen.memorizewords.data.repository.practice

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInBusinessCalendar
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.PracticeSessionSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.repository.practice.PracticeSessionRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.google.gson.Gson

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSessionRecordRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val practiceSessionRecordDao: PracticeSessionRecordDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : PracticeSessionRecordRepository {

    override suspend fun saveSessionRecord(record: PracticeSessionRecord) {
        val date = checkInBusinessCalendar.currentBusinessDate()
        appDatabase.withTransaction {
            val recordId = practiceSessionRecordDao.insert(record.toEntity(date))
            val targetId = if (recordId > 0L) recordId else record.id
            if (targetId > 0L) {
                syncOutboxDao.upsert(
                    syncOutboxEntity(
                        bizType = SyncOutboxBizType.PRACTICE_SESSION,
                        bizKey = "practice_session:$targetId",
                        operation = SyncOutboxOperation.UPSERT,
                        payload = gson.toJson(record.toSyncPayload(id = targetId, date = date))
                    )
                )
            }
        }
        syncOutboxWorkScheduler.scheduleDrain()
    }

    override fun getRecentSessionRecords(dayCount: Int): Flow<List<PracticeSessionRecord>> {
        return checkInBusinessCalendar.observeResolvedConfig()
            .distinctUntilChanged()
            .flatMapLatest { config ->
                val range = checkInBusinessCalendar.buildRecentDateRange(dayCount, config)
                practiceSessionRecordDao.getRecentSessions(range.startDate, range.endDate)
                    .map { list -> list.map { it.toDomain() } }
            }
    }

    override suspend fun getSessionRecord(recordId: Long): PracticeSessionRecord? {
        return practiceSessionRecordDao.getSessionById(recordId)?.toDomain()
    }
}

internal fun PracticeSessionRecord.toEntity(date: String): PracticeSessionRecordEntity {
    return PracticeSessionRecordEntity(
        id = id,
        date = date,
        mode = mode.name,
        entryType = entryType.name,
        entryCount = entryCount,
        durationMs = durationMs,
        createdAt = createdAt,
        wordIds = wordIds,
        questionCount = questionCount,
        completedCount = completedCount,
        correctCount = correctCount,
        submitCount = submitCount
    )
}

internal fun PracticeSessionRecord.toSyncPayload(id: Long, date: String): PracticeSessionSyncPayload {
    return PracticeSessionSyncPayload(
        id = id,
        date = date,
        mode = mode.name,
        entryType = entryType.name,
        entryCount = entryCount,
        durationMs = durationMs,
        createdAt = createdAt,
        wordIds = wordIds,
        questionCount = questionCount,
        completedCount = completedCount,
        correctCount = correctCount,
        submitCount = submitCount
    )
}

internal fun PracticeSessionRecordEntity.toDomain(): PracticeSessionRecord {
    val mode = runCatching { PracticeMode.valueOf(mode) }.getOrDefault(PracticeMode.LISTENING)
    val entryType = runCatching { PracticeEntryType.valueOf(entryType) }
        .getOrDefault(PracticeEntryType.RANDOM)
    return PracticeSessionRecord(
        id = id,
        date = date,
        mode = mode,
        entryType = entryType,
        entryCount = entryCount,
        durationMs = durationMs,
        createdAt = createdAt,
        wordIds = wordIds,
        questionCount = questionCount,
        completedCount = completedCount,
        correctCount = correctCount,
        submitCount = submitCount
    )
}
