package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.repository.sync.DailyStudySyncSnapshot
import com.chen.memorizewords.domain.study.repository.sync.StudySyncPort
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import javax.inject.Inject

class StudySyncPortImpl @Inject constructor(
    private val remote: RemoteUserSyncDataSource,
    private val launcher: DirectSyncLauncher
) : StudySyncPort {
    override fun scheduleDailyStudy(snapshot: DailyStudySyncSnapshot) {
        launcher.launch(
            operation = "daily_study",
            orderingKey = "study-day:${snapshot.date}",
            request = {
                remote.upsertDailyStudyDuration(
                    date = snapshot.date,
                    totalDurationMs = snapshot.totalDurationMs,
                    updatedAtMs = snapshot.updatedAtMs,
                    isNewPlanCompleted = snapshot.isNewPlanCompleted,
                    isReviewPlanCompleted = snapshot.isReviewPlanCompleted
                )
            }
        )
    }

    override fun scheduleCheckIn(record: CheckInRecord, onSuccess: () -> Unit) {
        launcher.launch(
            operation = "checkin",
            orderingKey = "study-day:${record.date}",
            request = {
                remote.upsertCheckInRecord(
                    date = record.date,
                    type = record.type.name,
                    signedAtMs = record.signedAtMs,
                    updatedAtMs = record.updatedAtMs
                )
            },
            onSuccess = { onSuccess() }
        )
    }

    override fun scheduleStudyPlan(plan: StudyPlan) {
        launcher.launch(
            operation = "study_plan",
            orderingKey = "study_plan",
            request = { remote.updateStudyPlan(plan) }
        )
    }
}
