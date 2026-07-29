package com.chen.memorizewords.data.study.repository.local

import androidx.room.withTransaction
import com.chen.memorizewords.core.common.time.AppClock
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import javax.inject.Inject
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider

class StudyRecordLocalStore @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val businessDateProvider: BusinessDateProvider,
    private val clock: AppClock
) {
    suspend fun addStudyDuration(durationMs: Long): LocalWriteResult {
        if (durationMs <= 0L) return LocalWriteResult()
        val date = businessDateProvider.currentBusinessDate()
        val snapshot = studyDatabase.withTransaction {
            val updatedAtMs = clock.nowEpochMillis()
            dailyStudyDurationDao.addDuration(
                date = date,
                durationMs = durationMs,
                updatedAtMs = updatedAtMs
            )
            dailyStudyDurationDao.getByDate(date)
        }
        return LocalWriteResult(dailyStudyDuration = snapshot)
    }

    suspend fun upsertCheckInRecord(entity: CheckInRecordEntity): LocalWriteResult {
        studyDatabase.withTransaction {
            checkInRecordDao.upsert(entity)
        }
        return LocalWriteResult(checkInRecord = entity)
    }
}
