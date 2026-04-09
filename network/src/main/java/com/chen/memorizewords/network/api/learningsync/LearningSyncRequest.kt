package com.chen.memorizewords.network.api.learningsync

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.model.PageData
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
import com.chen.memorizewords.network.util.awaitNullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningSyncRequest @Inject constructor(
    private val apiService: LearningSyncApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun getPracticeSettings(): NetworkResult<PracticeSettingsDto?> = requestExecutor.executeAuthenticated {
        apiService.getPracticeSettings()
            .awaitNullable<ApiResponse<PracticeSettingsDto?>, PracticeSettingsDto>()
    }

    suspend fun updatePracticeSettings(request: PracticeSettingsSyncRequest): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.updatePracticeSettings(request)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun upsertPracticeDuration(
        date: String,
        request: PracticeDurationSyncRequest
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertPracticeDuration(date, request)
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getPracticeDurations(
        page: Int,
        count: Int
    ): NetworkResult<PageData<PracticeDurationDto>> = requestExecutor.executeAuthenticated {
        apiService.getPracticeDurations(page = page, count = count)
            .await<ApiResponse<PageData<PracticeDurationDto>>, PageData<PracticeDurationDto>>()
    }

    suspend fun appendPracticeSession(request: PracticeSessionSyncRequest): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.appendPracticeSession(request)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun getPracticeSessions(
        page: Int,
        count: Int
    ): NetworkResult<PageData<PracticeSessionDto>> = requestExecutor.executeAuthenticated {
        apiService.getPracticeSessions(page = page, count = count)
            .await<ApiResponse<PageData<PracticeSessionDto>>, PageData<PracticeSessionDto>>()
    }

    suspend fun getFloatingSettings(): NetworkResult<FloatingSettingsDto?> = requestExecutor.executeAuthenticated {
        apiService.getFloatingSettings()
            .awaitNullable<ApiResponse<FloatingSettingsDto?>, FloatingSettingsDto>()
    }

    suspend fun updateFloatingSettings(request: FloatingSettingsSyncRequest): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.updateFloatingSettings(request)
                .await<ApiResponse<Unit>, Unit>()
        }

    suspend fun upsertFloatingDisplayRecord(
        date: String,
        request: FloatingDisplayRecordSyncRequest
    ): NetworkResult<Unit> = requestExecutor.executeAuthenticated {
        apiService.upsertFloatingDisplayRecord(date, request)
            .await<ApiResponse<Unit>, Unit>()
    }

    suspend fun getFloatingDisplayRecords(
        page: Int,
        count: Int
    ): NetworkResult<PageData<FloatingDisplayRecordDto>> = requestExecutor.executeAuthenticated {
        apiService.getFloatingDisplayRecords(page = page, count = count)
            .await<ApiResponse<PageData<FloatingDisplayRecordDto>>, PageData<FloatingDisplayRecordDto>>()
    }
}
