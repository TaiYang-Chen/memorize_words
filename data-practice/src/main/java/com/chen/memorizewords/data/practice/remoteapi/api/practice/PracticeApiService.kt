package com.chen.memorizewords.data.practice.remoteapi.api.practice

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
    val requestId: String,
    val referenceText: String,
    val provider: String,
    val audioBase64: String,
    val audioFormat: String? = null,
    val durationMs: Long? = null,
    val waveformSamples: List<Int>? = null
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
    val accuracyScore: Int? = null,
    val standardScore: Int? = null,
    val guidanceText: String? = null,
    val analysisSource: String? = null,
    val detailSourceNote: String? = null,
    val audioIssues: List<ShadowingAudioIssueDto>? = null,
    val phoneDetails: List<ShadowingDetailDto>? = null,
    val syllableDetails: List<ShadowingDetailDto>? = null,
    val wordDetails: List<ShadowingDetailDto>? = null,
    val recordingQuality: RecordingQualityDto? = null,
    val rawProviderTraceId: String? = null,
    val evaluationUsage: EvaluationUsageDto? = null
)

@JsonClass(generateAdapter = false)
data class ShadowingDetailDto(
    val text: String? = null,
    val score: Int? = null,
    val expected: String? = null,
    val actual: String? = null,
    val issueType: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = false)
data class RecordingQualityDto(
    val volumeScore: Int? = null,
    val speechRatio: Int? = null,
    val durationMs: Long? = null,
    val level: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = false)
data class EvaluationPolicyDto(
    val freeDailyLimit: Int,
    val memberDailyLimit: Int
)

@JsonClass(generateAdapter = false)
data class EvaluationUsageDto(
    val tier: String,
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int,
    val resetAtMs: Long,
    val policy: EvaluationPolicyDto
)

@JsonClass(generateAdapter = false)
data class PracticeUsageDto(
    val serverTimeMs: Long,
    val tts: TtsUsageDto,
    val evaluation: EvaluationUsageDto
)

@JsonClass(generateAdapter = false)
data class TtsUsageDto(
    val available: Boolean,
    val unlimitedDaily: Boolean
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
        const val PATH_USAGE = "practice/usage"
    }

    @POST(PATH_TTS)
    fun synthesize(@Body request: TtsRequestDto): Call<ApiResponse<TtsResponseDto>>

    @POST(PATH_SHADOWING_EVALUATE)
    fun evaluateShadowing(
        @Body request: ShadowingEvaluateRequestDto
    ): Call<ApiResponse<ShadowingEvaluateResponseDto>>

    @GET(PATH_PROVIDERS)
    fun getProviders(): Call<ApiResponse<ProviderListDto>>

    @GET(PATH_USAGE)
    fun getUsage(): Call<ApiResponse<PracticeUsageDto>>
}
