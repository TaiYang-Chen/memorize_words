package com.chen.memorizewords.data.practice.remoteapi.api.practice

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PracticeUsageRequest @Inject constructor(
    private val apiService: PracticeApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun getUsage(): NetworkResult<PracticeUsageDto> = requestExecutor.executeAuthenticated {
        apiService.getUsage().await<ApiResponse<PracticeUsageDto>, PracticeUsageDto>()
    }
}
