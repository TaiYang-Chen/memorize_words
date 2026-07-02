package com.chen.memorizewords.speech.api

sealed interface SpeechResult {
    val provider: SpeechProviderType
    val traceId: String
}

sealed interface SpeechSuccess : SpeechResult

sealed interface SpeechFailureResult : SpeechResult {
    val failure: SpeechFailure
    val causeCode: String?
    val message: String?
}

sealed interface SpeechAudioSuccess : SpeechSuccess {
    val audioOutput: SpeechAudioOutput
    val cacheKey: String
    val isFromCache: Boolean
}

data class WordAudioResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    override val audioOutput: SpeechAudioOutput,
    override val cacheKey: String,
    override val isFromCache: Boolean
) : SpeechAudioSuccess

data class SentenceAudioResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    override val audioOutput: SpeechAudioOutput,
    override val cacheKey: String,
    override val isFromCache: Boolean
) : SpeechAudioSuccess

data class ShadowingEvaluationResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    val totalScore: Int,
    val pronunciationScore: Int,
    val fluencyScore: Int,
    val recognizedText: String,
    val intonationScore: Int? = null,
    val stressScore: Int? = null,
    val speedScore: Int? = null,
    val accuracyScore: Int? = null,
    val standardScore: Int? = null,
    val audioIssues: List<ShadowingAudioIssue> = emptyList(),
    val phoneDetails: List<ShadowingDetail> = emptyList(),
    val syllableDetails: List<ShadowingDetail> = emptyList(),
    val wordDetails: List<ShadowingDetail> = emptyList(),
    val recordingQuality: ShadowingRecordingQuality? = null,
    val rawProviderTraceId: String? = null,
    val analysisSource: ShadowingAnalysisSource = ShadowingAnalysisSource.PROVIDER_ONLY,
    val detailSourceNote: String? = null,
    val guidanceText: String? = null
) : SpeechSuccess

data class DefaultSpeechFailureResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    override val failure: SpeechFailure,
    override val causeCode: String? = null,
    override val message: String? = null
) : SpeechFailureResult
