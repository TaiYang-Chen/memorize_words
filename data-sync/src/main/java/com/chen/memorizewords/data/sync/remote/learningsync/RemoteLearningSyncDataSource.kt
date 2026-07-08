package com.chen.memorizewords.data.sync.remote.learningsync

import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDisplayRecordDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventRequest
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningEventResultDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeDurationDto
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.PracticeSessionDto
import com.chen.memorizewords.core.network.http.PageData

interface RemoteLearningSyncDataSource {
    suspend fun recordLearningEvent(request: LearningEventRequest): Result<LearningEventResultDto>

    suspend fun getPracticeSettings(): Result<PracticeSettings?>

    suspend fun updatePracticeSettings(settings: PracticeSettings): Result<Unit>

    suspend fun getPracticeDurations(page: Int, count: Int): Result<PageData<PracticeDurationDto>>

    suspend fun upsertPracticeDuration(
        date: String,
        totalDurationMs: Long,
        updatedAt: Long
    ): Result<Unit>

    suspend fun appendPracticeSession(record: PracticeSessionRecord): Result<Unit>

    suspend fun getPracticeSessions(page: Int, count: Int): Result<PageData<PracticeSessionDto>>

    suspend fun getFloatingSettings(): Result<FloatingWordSettings?>

    suspend fun updateFloatingSettings(settings: FloatingWordSettings): Result<Unit>

    suspend fun getFloatingDisplayRecords(
        page: Int,
        count: Int
    ): Result<PageData<FloatingDisplayRecordDto>>

    suspend fun upsertFloatingDisplayRecord(record: FloatingWordDisplayRecord): Result<Unit>
}
