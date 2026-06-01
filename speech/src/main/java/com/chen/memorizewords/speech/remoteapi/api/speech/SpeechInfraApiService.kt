package com.chen.memorizewords.speech.remoteapi.api.speech

import com.chen.memorizewords.core.network.http.ApiResponse
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.GET

@JsonClass(generateAdapter = false)
data class AliyunTokenDto(
    val token: String,
    val expireAt: Long
)

interface SpeechInfraApiService {
    companion object {
        const val PATH_ALIYUN_TOKEN = "speech/providers/aliyun/token"
    }

    @GET(PATH_ALIYUN_TOKEN)
    fun getAliyunToken(): Call<ApiResponse<AliyunTokenDto>>
}
