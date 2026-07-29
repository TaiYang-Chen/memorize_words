package com.chen.memorizewords.domain.study.repository

import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords

interface StudyRecordSnapshotPort {
    suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>)
    suspend fun upsertStudyRecordsFromRemote(records: List<DailyStudyRecords>)
}
