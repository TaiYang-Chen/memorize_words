package com.chen.memorizewords.data.study.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.repository.DailyStudySnapshotPort
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import javax.inject.Inject

class DailyStudySnapshotLocalStateStore @Inject constructor(
    private val database: StudyDatabase,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao
) : DailyStudySnapshotPort {
    override suspend fun overwriteDailyDurationsFromRemote(
        durations: List<StudyDailyDurationSnapshot>
    ) {
        database.withTransaction {
            dailyStudyDurationDao.deleteAll()
            if (durations.isNotEmpty()) {
                dailyStudyDurationDao.upsertAll(durations.map(StudyDailyDurationSnapshot::toEntity))
            }
        }
    }

    override suspend fun upsertDailyDurationsFromRemote(
        durations: List<StudyDailyDurationSnapshot>
    ) {
        if (durations.isEmpty()) return
        database.withTransaction {
            dailyStudyDurationDao.upsertAll(durations.map(StudyDailyDurationSnapshot::toEntity))
        }
    }

    override suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>) {
        database.withTransaction {
            checkInRecordDao.deleteAll()
            if (records.isNotEmpty()) {
                checkInRecordDao.upsertAll(records.map(CheckInRecord::toEntity))
            }
        }
    }
}

private fun CheckInRecord.toEntity(): CheckInRecordEntity = CheckInRecordEntity(
    date = date,
    type = type,
    signedAtMs = signedAtMs,
    updatedAtMs = updatedAtMs
)

private fun StudyDailyDurationSnapshot.toEntity(): DailyStudyDurationEntity =
    DailyStudyDurationEntity(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAtMs = updatedAtMs,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
