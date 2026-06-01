package com.chen.memorizewords.data.floating.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.floating.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.floating.repository.toDisplayWordEntities
import com.chen.memorizewords.domain.floating.FloatingSnapshotLocalStatePort
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import javax.inject.Inject

class FloatingSnapshotLocalStateStore @Inject constructor(
    private val floatingDatabase: FloatingDatabase,
    private val dao: FloatingWordDisplayRecordDao
) : FloatingSnapshotLocalStatePort {

    override suspend fun overwriteDisplayRecordsFromRemote(records: List<FloatingWordDisplayRecord>) {
        floatingDatabase.withTransaction {
            dao.deleteAll()
            if (records.isNotEmpty()) {
                dao.upsertAll(
                    records.map { record ->
                        FloatingWordDisplayRecordEntity(
                            date = record.date,
                            displayCount = record.displayCount,
                            updatedAt = record.updatedAt
                        )
                    }
                )
                dao.upsertWords(
                    records.flatMap { record -> record.wordIds.toDisplayWordEntities(record.date) }
                )
            }
        }
    }
}
