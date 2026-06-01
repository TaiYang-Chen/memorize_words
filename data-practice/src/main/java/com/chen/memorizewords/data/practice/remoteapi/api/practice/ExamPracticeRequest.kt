package com.chen.memorizewords.data.practice.remoteapi.api.practice

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamPracticeRequest @Inject constructor(
    private val apiService: ExamPracticeApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun getWordPractice(wordId: Long): NetworkResult<ExamPracticeWordResponseDto> =
        requestExecutor.executeAuthenticated {
            apiService.getWordPractice(wordId)
                .await<ApiResponse<ExamPracticeWordResponseDto>, ExamPracticeWordResponseDto>()
        }

    suspend fun updateFavorite(itemId: Long, favorite: Boolean): NetworkResult<ExamItemStateDto> =
        requestExecutor.executeAuthenticated {
            apiService.updateFavorite(itemId, ExamItemFavoriteRequest(favorite))
                .await<ApiResponse<ExamItemStateDto>, ExamItemStateDto>()
        }

    suspend fun submitSession(request: ExamPracticeSessionSubmitRequest): NetworkResult<Unit> =
        requestExecutor.executeAuthenticated {
            apiService.submitSession(request)
                .await<ApiResponse<Unit>, Unit>()
        }
}
