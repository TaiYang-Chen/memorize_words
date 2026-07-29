package com.chen.memorizewords.data.wordbook.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordEntity
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyRecordSnapshotPort
import javax.inject.Inject

class StudyRecordSnapshotLocalStateStore @Inject constructor(
    private val database: WordBookDatabase,
    private val dao: WordStudyRecordDao
) : StudyRecordSnapshotPort {
    override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        database.withTransaction {
            dao.deleteAll()
            if (records.isNotEmpty()) dao.upsertAll(records.map(DailyStudyRecords::toEntity))
        }
    }

    override suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>) {
        if (records.isEmpty()) return
        database.withTransaction {
            dao.upsertAll(records.map(DailyStudyRecords::toEntity))
        }
    }
}

private fun DailyStudyRecords.toEntity(): WordStudyRecordEntity = WordStudyRecordEntity(
    date = date,
    wordId = wordId,
    word = word,
    definition = definition,
    isNewWord = isNewWord
)
