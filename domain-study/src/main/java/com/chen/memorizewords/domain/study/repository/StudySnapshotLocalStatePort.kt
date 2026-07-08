package com.chen.memorizewords.domain.study.repository

import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords

data class StudyDailyDurationSnapshot(
    val date: String,
    val totalDurationMs: Long,
    val updatedAt: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

interface StudySnapshotLocalStatePort {
    suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>)
    suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>)
    suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>)
    suspend fun overwriteDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>)
    suspend fun upsertDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>)
    suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>)
}
