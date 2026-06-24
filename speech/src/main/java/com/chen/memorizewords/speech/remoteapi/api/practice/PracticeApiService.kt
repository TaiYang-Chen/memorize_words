package com.chen.memorizewords.speech.remoteapi.api.practice

import com.chen.memorizewords.core.network.http.ApiResponse
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
    val provider: String,
    val speed: Int? = null,
    val pitch: Int? = null,
    val volume: Int? = null,
    val audioFormat: String? = null,
    val audioCtrl: String? = null,
    val textCtrl: String? = null
)

@JsonClass(generateAdapter = false)
data class TtsResponseDto(
    val provider: String? = null,
    val audioUrl: String?,
    val cacheKey: String?,
    val audioFormat: String? = null,
    val mimeType: String? = null,
    val fromCache: Boolean? = null
)

@JsonClass(generateAdapter = false)
data class ShadowingEvaluateRequestDto(
    val word: String,
    val provider: String,
    val audioBase64: String
)

@JsonClass(generateAdapter = false)
data class ShadowingAudioIssueDto(
    val type: String?,
    val severity: String?,
    val message: String?
)

@JsonClass(generateAdapter = false)
data class ShadowingEvaluateResponseDto(
    val totalScore: Int,
    val pronunciationScore: Int,
    val fluencyScore: Int,
    val recognizedText: String,
    val intonationScore: Int? = null,
    val stressScore: Int? = null,
    val speedScore: Int? = null,
    val guidanceText: String? = null,
    val analysisSource: String? = null,
    val detailSourceNote: String? = null,
    val audioIssues: List<ShadowingAudioIssueDto>? = null
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
