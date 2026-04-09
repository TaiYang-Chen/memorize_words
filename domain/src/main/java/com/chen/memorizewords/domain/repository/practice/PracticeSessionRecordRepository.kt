package com.chen.memorizewords.domain.repository.practice

import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import kotlinx.coroutines.flow.Flow

interface PracticeSessionRecordRepository {
    suspend fun saveSessionRecord(record: PracticeSessionRecord)
    fun getRecentSessionRecords(dayCount: Int): Flow<List<PracticeSessionRecord>>
    suspend fun getSessionRecord(recordId: Long): PracticeSessionRecord?
}
