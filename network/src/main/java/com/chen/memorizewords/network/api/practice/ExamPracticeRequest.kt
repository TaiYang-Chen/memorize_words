package com.chen.memorizewords.network.api.practice

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
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
