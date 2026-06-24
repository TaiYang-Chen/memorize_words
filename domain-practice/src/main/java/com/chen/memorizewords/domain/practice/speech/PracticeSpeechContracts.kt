package com.chen.memorizewords.domain.practice.speech

enum class SpeechProviderType {
    BAIDU,
    ALIYUN
}

enum class SpeechCapability {
    WORD_TTS,
    SENTENCE_TTS,
    SHADOWING_EVALUATION
}

interface PracticeSpeechSynthesizer {
    suspend fun synthesize(task: SpeechTask): SpeechResult
}

interface ShadowingEvaluator {
    suspend fun evaluate(task: SpeechTask.EvaluateShadowing): SpeechResult
}

sealed interface SpeechTask {
    val requiredCapability: SpeechCapability

    data class SynthesizeWord(
        val text: String,
        val locale: String = "en-US",
        val voice: String = "default",
        val audioFormat: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.WORD_TTS
    }

    data class SynthesizeSentence(
        val text: String,
        val locale: String = "en-US",
        val voice: String = "default",
        val audioFormat: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.SENTENCE_TTS
    }

    data class EvaluateShadowing(
        val referenceText: String,
        val audioInput: SpeechAudioInput,
        val locale: String = "en-US",
        val recordingMetadata: ShadowingRecordingMetadata = ShadowingRecordingMetadata()
    ) : SpeechTask {
        override val requiredCapability: SpeechCapability = SpeechCapability.SHADOWING_EVALUATION
    }
}

sealed interface SpeechAudioInput {
    data class FileInput(
        val filePath: String,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput

    data class ByteArrayInput(
        val bytes: ByteArray,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput

    data class StreamInput(
        val streamId: String,
        val format: SpeechAudioFormat = SpeechAudioFormat.defaultInput()
    ) : SpeechAudioInput
}

sealed interface SpeechAudioOutput {
    val format: SpeechAudioFormat

    data class FileOutput(
        val filePath: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput

    data class UrlOutput(
        val url: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput

    data class StreamOutput(
        val streamId: String,
        override val format: SpeechAudioFormat = SpeechAudioFormat.defaultOutput()
    ) : SpeechAudioOutput
}

data class SpeechAudioFormat(
    val mimeType: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: String
) {
    companion object {
        fun defaultOutput(): SpeechAudioFormat {
            return SpeechAudioFormat(
                mimeType = "audio/mpeg",
                sampleRateHz = 16000,
                channelCount = 1,
                encoding = "mp3"
            )
        }

        fun defaultInput(): SpeechAudioFormat {
            return SpeechAudioFormat(
                mimeType = "audio/mp4",
                sampleRateHz = 16000,
                channelCount = 1,
                encoding = "aac"
            )
        }
    }
}

sealed class SpeechFailure {
    data class AuthFailure(val message: String? = null) : SpeechFailure()
    data class NetworkFailure(val message: String? = null) : SpeechFailure()
    data class ProviderFailure(val message: String? = null) : SpeechFailure()
    data class Unsupported(val message: String? = null) : SpeechFailure()
    data class InvalidRequest(val message: String? = null) : SpeechFailure()
    data class Unknown(
        val message: String? = null,
        val causeCode: String? = null
    ) : SpeechFailure()
}

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

data class DefaultSpeechFailureResult(
    override val provider: SpeechProviderType,
    override val traceId: String,
    override val failure: SpeechFailure,
    override val causeCode: String? = null,
    override val message: String? = null
) : SpeechFailureResult

data class ShadowingRecordingMetadata(
    val durationMs: Long = 0L,
    val waveformSamples: List<Int> = emptyList()
)

enum class ShadowingAnalysisSource {
    PROVIDER_ONLY,
    LOCAL_PLACEHOLDER,
    PROVIDER_PLUS_LOCAL
}

enum class ShadowingAudioIssueSeverity {
    INFO,
    WARNING
}

enum class ShadowingAudioIssueType {
    LOW_VOLUME,
    MOSTLY_SILENT,
    TOO_FAST,
    TOO_SLOW,
    ENVIRONMENT_NOISE
}

data class ShadowingAudioIssue(
    val type: ShadowingAudioIssueType,
    val severity: ShadowingAudioIssueSeverity,
    val message: String? = null
)

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
    val audioIssues: List<ShadowingAudioIssue> = emptyList(),
    val analysisSource: ShadowingAnalysisSource = ShadowingAnalysisSource.PROVIDER_ONLY,
    val detailSourceNote: String? = null,
    val guidanceText: String? = null
) : SpeechSuccess
