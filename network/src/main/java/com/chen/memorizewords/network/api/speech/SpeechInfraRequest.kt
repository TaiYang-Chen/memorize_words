package com.chen.memorizewords.network.api.speech

import com.chen.memorizewords.network.api.NetworkRequestExecutor
import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.network.util.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechInfraRequest @Inject constructor(
    private val speechInfraApiService: SpeechInfraApiService,
    private val requestExecutor: NetworkRequestExecutor
) {

    suspend fun getAliyunToken(): NetworkResult<AliyunTokenDto> =
        requestExecutor.executeAuthenticated {
            speechInfraApiService.getAliyunToken()
                .await<ApiResponse<AliyunTokenDto>, AliyunTokenDto>()
        }
}
