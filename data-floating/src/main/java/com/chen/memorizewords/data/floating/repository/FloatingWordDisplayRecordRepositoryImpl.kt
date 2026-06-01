package com.chen.memorizewords.data.floating.repository

import androidx.room.withTransaction
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordWithWords
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayWordEntity
import com.chen.memorizewords.domain.sync.FloatingDisplayRecordSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.google.gson.Gson
import javax.inject.Inject

class FloatingWordDisplayRecordRepositoryImpl @Inject constructor(
    private val floatingDatabase: FloatingDatabase,
    private val dao: FloatingWordDisplayRecordDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
) : FloatingWordDisplayRecordRepository {

    override suspend fun recordDisplay(wordId: Long) {
        if (wordId <= 0L) return
        val date = checkInBusinessCalendar.currentBusinessDate()
        floatingDatabase.withTransaction {
            val current = dao.getByDate(date)
            val currentWordIds = current?.words?.sortedBy { it.sequence }?.map { it.wordId }.orEmpty()
            val updated = if (current == null) {
                FloatingWordDisplayRecordEntity(
                    date = date,
                    displayCount = 1,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                current.record.copy(
                    displayCount = current.record.displayCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
            }
            val updatedWordIds = currentWordIds + wordId
            dao.upsert(updated)
            dao.deleteWordsByDates(listOf(date))
            val words = updatedWordIds.toDisplayWordEntities(date)
            if (words.isNotEmpty()) {
                dao.upsertWords(words)
            }
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.FLOATING_DISPLAY_RECORD,
                bizKey = "floating_display_record:${updated.date}",
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(
                    FloatingDisplayRecordSyncPayload(
                        date = updated.date,
                        displayCount = updated.displayCount,
                        wordIds = updatedWordIds,
                        updatedAt = updated.updatedAt
                    )
                )
            )
        }
    }

    override suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord? {
        val entity = dao.getByDate(date) ?: return null
        return entity.toDomain()
    }
}

internal fun FloatingWordDisplayRecordWithWords.toDomain(): FloatingWordDisplayRecord {
    return FloatingWordDisplayRecord(
        date = record.date,
        displayCount = record.displayCount,
        wordIds = words.sortedBy { it.sequence }.map { it.wordId },
        updatedAt = record.updatedAt
    )
}

internal fun List<Long>.toDisplayWordEntities(date: String): List<FloatingWordDisplayWordEntity> {
    return mapIndexed { index, wordId ->
        FloatingWordDisplayWordEntity(
            recordDate = date,
            sequence = index,
            wordId = wordId
        )
    }
}
