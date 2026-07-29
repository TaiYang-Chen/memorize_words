package com.chen.memorizewords.data.study.repository.record

import androidx.room.withTransaction
import com.chen.memorizewords.core.common.time.AppClock
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.evaluateDailyPlanCompletion
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.sync.DailyStudySyncSnapshot
import com.chen.memorizewords.domain.study.repository.sync.StudySyncPort
import javax.inject.Inject

class DailyStudyProjectionStoreImpl @Inject constructor(
    private val database: StudyDatabase,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val clock: AppClock,
    private val studySyncPort: StudySyncPort
) : DailyStudyProjectionStore {

    override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition {
        val committed = database.withTransaction {
            applyInTransaction(evaluation)
        }
        committed.dailyStudy?.let(studySyncPort::scheduleDailyStudy)
        committed.checkIn?.let(studySyncPort::scheduleCheckIn)
        return committed.transition
    }

    private suspend fun applyInTransaction(evaluation: DailyProgressEvaluation): ProjectionCommit {
        val existingDaily = dailyStudyDurationDao.getByDate(evaluation.businessDate)
        val decision = evaluateDailyPlanCompletion(
            existingNewCompleted = existingDaily?.isNewPlanCompleted == true,
            existingReviewCompleted = existingDaily?.isReviewPlanCompleted == true,
            counts = evaluation.counts,
            targets = evaluation.targets
        )
        val now = clock.nowEpochMillis()
        if (
            existingDaily == null &&
            !decision.isCheckInEligible &&
            evaluation.counts.newCount == 0 &&
            evaluation.counts.reviewCount == 0
        ) {
            return ProjectionCommit(
                transition = DailyProgressTransition.NotEligible(evaluation.businessDate)
            )
        }
        dailyStudyDurationDao.upsertPlanCompletion(
            date = evaluation.businessDate,
            isNewCompleted = decision.isNewPlanCompleted.toInt(),
            isReviewCompleted = decision.isReviewPlanCompleted.toInt(),
            updatedAtMs = now
        )
        val daily = checkNotNull(dailyStudyDurationDao.getByDate(evaluation.businessDate))
        val dailySnapshot = DailyStudySyncSnapshot(
            date = daily.date,
            totalDurationMs = daily.totalDurationMs,
            updatedAtMs = daily.updatedAtMs,
            isNewPlanCompleted = daily.isNewPlanCompleted,
            isReviewPlanCompleted = daily.isReviewPlanCompleted
        )

        if (!decision.isCheckInEligible) {
            return ProjectionCommit(
                transition = DailyProgressTransition.NotEligible(evaluation.businessDate),
                dailyStudy = dailySnapshot
            )
        }

        val candidate = CheckInRecordEntity(
            date = evaluation.businessDate,
            type = CheckInType.AUTO,
            signedAtMs = now,
            updatedAtMs = now
        )
        val inserted = checkInRecordDao.insertIgnore(candidate) != INSERT_IGNORED
        val committedCheckIn = if (inserted) {
            candidate
        } else {
            checkNotNull(checkInRecordDao.getByDate(evaluation.businessDate))
        }
        val record = committedCheckIn.toDomain()
        val transition = if (inserted) {
            DailyProgressTransition.CheckInCreated(evaluation.businessDate, record)
        } else {
            DailyProgressTransition.AlreadyCheckedIn(evaluation.businessDate, record)
        }
        return ProjectionCommit(
            transition = transition,
            dailyStudy = dailySnapshot,
            checkIn = record
        )
    }

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}

private data class ProjectionCommit(
    val transition: DailyProgressTransition,
    val dailyStudy: DailyStudySyncSnapshot? = null,
    val checkIn: CheckInRecord? = null
)

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun CheckInRecordEntity.toDomain(): CheckInRecord = CheckInRecord(
    date = date,
    type = type,
    signedAtMs = signedAtMs,
    updatedAtMs = updatedAtMs
)
