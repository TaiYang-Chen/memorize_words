package com.chen.memorizewords.data.remote.learningsync

import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.network.api.learningsync.FloatingDisplayRecordDto
import com.chen.memorizewords.network.api.learningsync.PracticeDurationDto
import com.chen.memorizewords.network.api.learningsync.PracticeSessionDto
import com.chen.memorizewords.network.model.PageData

interface RemoteLearningSyncDataSource {
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
