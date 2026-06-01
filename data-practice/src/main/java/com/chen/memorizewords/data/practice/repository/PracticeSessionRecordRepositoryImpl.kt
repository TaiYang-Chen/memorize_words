package com.chen.memorizewords.data.practice.repository

import androidx.room.withTransaction
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.data.practice.local.PracticeDatabase
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordWithWords
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionWordEntity
import com.chen.memorizewords.domain.sync.PracticeSessionSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSessionRecordRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSessionRecordRepositoryImpl @Inject constructor(
    private val practiceDatabase: PracticeDatabase,
    private val practiceSessionRecordDao: PracticeSessionRecordDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
) : PracticeSessionRecordRepository {

    override suspend fun saveSessionRecord(record: PracticeSessionRecord) {
        val date = checkInBusinessCalendar.currentBusinessDate()
        practiceDatabase.withTransaction {
            val recordId = practiceSessionRecordDao.insert(record.toEntity(date))
            val targetId = if (recordId > 0L) recordId else record.id
            if (targetId > 0L) {
                practiceSessionRecordDao.deleteWordsBySessionIds(listOf(targetId))
                val words = record.wordIds.toWordEntities(targetId)
                if (words.isNotEmpty()) {
                    practiceSessionRecordDao.upsertWords(words)
                }
                SyncOutboxWriter.enqueueLatest(
                    bizType = OutboxTopic.PRACTICE_SESSION,
                    bizKey = "practice_session:$targetId",
                    operation = SyncOperation.UPSERT,
                    payload = gson.toJson(record.toSyncPayload(id = targetId, date = date))
                )
            }
        }
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
        mode = mode,
        entryType = entryType,
        entryCount = entryCount,
        durationMs = durationMs,
        createdAt = createdAt,
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

internal fun PracticeSessionRecordWithWords.toDomain(): PracticeSessionRecord {
    return PracticeSessionRecord(
        id = record.id,
        date = record.date,
        mode = record.mode,
        entryType = record.entryType,
        entryCount = record.entryCount,
        durationMs = record.durationMs,
        createdAt = record.createdAt,
        wordIds = words.sortedBy { it.sequence }.map { it.wordId },
        questionCount = record.questionCount,
        completedCount = record.completedCount,
        correctCount = record.correctCount,
        submitCount = record.submitCount
    )
}

internal fun List<Long>.toWordEntities(sessionId: Long): List<PracticeSessionWordEntity> {
    return mapIndexed { index, wordId ->
        PracticeSessionWordEntity(
            sessionId = sessionId,
            sequence = index,
            wordId = wordId
        )
    }
}

internal fun parsePracticeMode(value: String): PracticeMode = PracticeMode.valueOf(value)

internal fun parsePracticeEntryType(value: String): PracticeEntryType = PracticeEntryType.valueOf(value)
