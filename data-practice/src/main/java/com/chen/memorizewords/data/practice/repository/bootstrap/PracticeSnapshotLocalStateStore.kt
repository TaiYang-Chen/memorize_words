package com.chen.memorizewords.data.practice.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.practice.local.PracticeDatabase
import com.chen.memorizewords.data.practice.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.practice.local.room.model.practice.daily.DailyPracticeDurationEntity
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.practice.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.practice.repository.toWordEntities
import com.chen.memorizewords.domain.practice.PracticeDailyDurationSnapshot
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSnapshotLocalStatePort
import javax.inject.Inject

class PracticeSnapshotLocalStateStore @Inject constructor(
    private val practiceDatabase: PracticeDatabase,
    private val dailyPracticeDurationDao: DailyPracticeDurationDao,
    private val practiceSessionRecordDao: PracticeSessionRecordDao
) : PracticeSnapshotLocalStatePort {

    override suspend fun overwriteDurationsFromRemote(durations: List<PracticeDailyDurationSnapshot>) {
        practiceDatabase.withTransaction {
            dailyPracticeDurationDao.deleteAll()
            if (durations.isNotEmpty()) {
                dailyPracticeDurationDao.upsertAll(
                    durations.map { duration ->
                        DailyPracticeDurationEntity(
                            date = duration.date,
                            totalDurationMs = duration.totalDurationMs,
                            updatedAt = duration.updatedAt
                        )
                    }
                )
            }
        }
    }

    override suspend fun overwriteSessionsFromRemote(records: List<PracticeSessionRecord>) {
        practiceDatabase.withTransaction {
            practiceSessionRecordDao.deleteAll()
            if (records.isNotEmpty()) {
                practiceSessionRecordDao.upsertAll(records.map(PracticeSessionRecord::toEntity))
                practiceSessionRecordDao.upsertWords(
                    records.flatMap { record -> record.wordIds.toWordEntities(record.id) }
                )
            }
        }
    }
}

private fun PracticeSessionRecord.toEntity(): PracticeSessionRecordEntity {
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
