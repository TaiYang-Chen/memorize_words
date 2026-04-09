package com.chen.memorizewords.network.api.practice

import com.chen.memorizewords.network.model.ApiResponse
import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = false)
data class TtsRequestDto(
    val text: String,
    val language: String,
    val voice: String,
    val provider: String
)

@JsonClass(generateAdapter = false)
data class TtsResponseDto(
    val audioUrl: String?,
    val audioBase64: String?,
    val cacheKey: String?
)

@JsonClass(generateAdapter = false)
data class ShadowingEvaluateRequestDto(
    val word: String,
    val provider: String,
    val audioBase64: String
)

@JsonClass(generateAdapter = false)
data class ShadowingEvaluateResponseDto(
    val totalScore: Int,
    val pronunciationScore: Int,
    val fluencyScore: Int,
    val recognizedText: String
)

@JsonClass(generateAdapter = false)
data class ProviderListDto(
    val providers: List<String>
)

interface PracticeApiService {
    companion object {
        const val PATH_TTS = "practice/tts"
        const val PATH_SHADOWING_EVALUATE = "practice/shadowing/evaluate"
        const val PATH_PROVIDERS = "practice/providers"
    }

    @POST(PATH_TTS)
    fun synthesize(@Body request: TtsRequestDto): Call<ApiResponse<TtsResponseDto>>

    @POST(PATH_SHADOWING_EVALUATE)
    fun evaluateShadowing(
        @Body request: ShadowingEvaluateRequestDto
    ): Call<ApiResponse<ShadowingEvaluateResponseDto>>

    @GET(PATH_PROVIDERS)
    fun getProviders(): Call<ApiResponse<ProviderListDto>>
}
