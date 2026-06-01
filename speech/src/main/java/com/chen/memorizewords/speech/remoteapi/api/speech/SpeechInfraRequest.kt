package com.chen.memorizewords.speech.remoteapi.api.speech

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
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
