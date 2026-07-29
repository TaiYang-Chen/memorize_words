package com.chen.memorizewords.domain.study.repository.sync

import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan

data class DailyStudySyncSnapshot(
    val date: String,
    val totalDurationMs: Long,
    val updatedAtMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

interface StudySyncPort {
    fun scheduleDailyStudy(snapshot: DailyStudySyncSnapshot)
    fun scheduleCheckIn(record: CheckInRecord, onSuccess: () -> Unit = {})
    fun scheduleStudyPlan(plan: StudyPlan)
}
