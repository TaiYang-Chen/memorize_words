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
    val recognizedText: String
) : SpeechSuccess

data class DefaultSpeechFailureResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    override val failure: SpeechFailure,
    override val causeCode: String? = null,
    override val message: String? = null
) : SpeechFailureResult
