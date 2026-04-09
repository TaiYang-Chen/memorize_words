package com.chen.memorizewords.data.repository.floating

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInBusinessCalendar
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.FloatingDisplayRecordSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.repository.floating.FloatingWordDisplayRecordRepository
import com.google.gson.Gson
import javax.inject.Inject

class FloatingWordDisplayRecordRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val dao: FloatingWordDisplayRecordDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : FloatingWordDisplayRecordRepository {

    override suspend fun recordDisplay(wordId: Long) {
        if (wordId <= 0L) return
        val date = checkInBusinessCalendar.currentBusinessDate()
        appDatabase.withTransaction {
            val current = dao.getByDate(date)
            val updated = if (current == null) {
                FloatingWordDisplayRecordEntity(
                    date = date,
                    displayCount = 1,
                    wordIds = listOf(wordId),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                current.copy(
                    displayCount = current.displayCount + 1,
                    wordIds = current.wordIds + wordId,
                    updatedAt = System.currentTimeMillis()
                )
            }
            dao.upsert(updated)
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.FLOATING_DISPLAY_RECORD,
                    bizKey = "floating_display_record:${updated.date}",
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(
                        FloatingDisplayRecordSyncPayload(
                            date = updated.date,
                            displayCount = updated.displayCount,
                            wordIds = updated.wordIds,
                            updatedAt = updated.updatedAt
                        )
                    )
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
    }

    override suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord? {
        val entity = dao.getByDate(date) ?: return null
        return FloatingWordDisplayRecord(
            date = entity.date,
            displayCount = entity.displayCount,
            wordIds = entity.wordIds,
            updatedAt = entity.updatedAt
        )
    }
}
