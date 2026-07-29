package com.chen.memorizewords.domain.study.repository

import com.chen.memorizewords.domain.study.model.record.CheckInRecord

interface DailyStudySnapshotPort {
    suspend fun overwriteDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>)
    suspend fun upsertDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>)
    suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>)
}
