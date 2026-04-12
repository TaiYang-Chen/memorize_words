package com.chen.memorizewords.speech

import com.chen.memorizewords.network.api.practice.PracticeSpeechRequest
import com.chen.memorizewords.network.api.practice.ShadowingAudioIssueDto
import com.chen.memorizewords.network.api.practice.ShadowingEvaluateResponseDto
import com.chen.memorizewords.network.util.NetworkResult
import com.chen.memorizewords.speech.api.ShadowingAnalysisSource
import com.chen.memorizewords.speech.api.ShadowingAudioIssue
import com.chen.memorizewords.speech.api.ShadowingAudioIssueSeverity
import com.chen.memorizewords.speech.api.ShadowingAudioIssueType
import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.SentenceAudioResult
import com.chen.memorizewords.speech.api.SpeechAudioInput
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechCapability
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechProviderAdapter
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.api.WordAudioResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaiduSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val ttsClient: BaiduTtsClient,
    private val shadowingClient: BaiduShadowingClient,
    private val shadowingScorer: BaiduShadowingScorer,
    private val practiceSpeechRequest: PracticeSpeechRequest
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.BAIDU
    override val capabilities: Set<SpeechCapability> = setOf(
        SpeechCapability.WORD_TTS,
        SpeechCapability.SENTENCE_TTS,
        SpeechCapability.SHADOWING_EVALUATION
    )

    override suspend fun execute(task: SpeechTask, traceId: String): SpeechResult {
        return when (task) {
            is SpeechTask.SynthesizeSentence -> synthesize(
                text = task.text,
                locale = task.locale,
                voice = task.voice,
                audioFormat = task.audioFormat,
                traceId = traceId,
                task = task
            )

            is SpeechTask.SynthesizeWord -> synthesize(
                text = task.text,
                locale = task.locale,
                voice = task.voice,
                audioFormat = task.audioFormat,
                traceId = traceId,
                task = task
            )

            is SpeechTask.EvaluateShadowing -> evaluate(task, traceId)
        }
    }

    private suspend fun synthesize(
        text: String,
        locale: String,
        voice: String,
        audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat,
        traceId: String,
        task: SpeechTask
    ): SpeechResult {
        return runCatching {
            val audioFile = ttsClient.synthesize(
                task = SpeechSynthesisTask(
                    text = text,
                    locale = locale,
                    voice = voice,
                    audioFormat = audioFormat
                ),
                traceId = traceId
            )
            val output = SpeechAudioOutput.FileOutput(
                filePath = audioFile.absolutePath,
                format = audioFormat
            )
            val cacheKey = speechCacheStore.stableHash(task.cacheDescriptor(provider.name))
            when (task) {
                is SpeechTask.SynthesizeWord -> WordAudioResult(
                    provider = provider,
                    traceId = traceId,
                    audioOutput = output,
                    cacheKey = cacheKey,
                    isFromCache = false
                )

                is SpeechTask.SynthesizeSentence -> SentenceAudioResult(
                    provider = provider,
                    traceId = traceId,
                    audioOutput = output,
                    cacheKey = cacheKey,
                    isFromCache = false
                )

                else -> error("Unexpected task type")
            }
        }.getOrElse { error ->
            mapError(traceId, error)
        }
    }

    private suspend fun evaluate(task: SpeechTask.EvaluateShadowing, traceId: String): SpeechResult {
        val normalizedInput = when (val input = task.audioInput) {
            is SpeechAudioInput.FileInput -> input
            is SpeechAudioInput.ByteArrayInput -> {
                val tempFile = speechCacheStore.createTempFile("shadowing_$traceId")
                tempFile.writeBytes(input.bytes)
                SpeechAudioInput.FileInput(
                    filePath = tempFile.absolutePath,
                    format = input.format
                )
            }

            is SpeechAudioInput.StreamInput -> {
                return failureResult(
                    provider = provider,
                    traceId = traceId,
                    failure = SpeechFailure.Unsupported("Stream input is not implemented."),
                    message = "Stream audio input is not supported yet."
                )
            }
        }
        val remoteResult = practiceSpeechRequest.evaluateShadowing(
            word = task.referenceText,
            provider = provider.name,
            audioFilePath = normalizedInput.filePath
        )
        if (remoteResult is NetworkResult.Success) {
            return remoteResult.data.toSpeechResult(
                provider = provider,
                traceId = traceId
            )
        }
        return runCatching {
            val recognized = shadowingClient.recognize(
                audioInput = normalizedInput,
                locale = task.locale
            )
            val scores = shadowingScorer.score(
                referenceText = task.referenceText,
                recognizedText = recognized.recognizedText,
                recordingMetadata = task.recordingMetadata
            )
            ShadowingEvaluationResult(
                provider = provider,
                traceId = traceId,
                totalScore = scores.totalScore,
                pronunciationScore = scores.pronunciationScore,
                fluencyScore = scores.fluencyScore,
                recognizedText = recognized.recognizedText,
                intonationScore = scores.intonationScore,
                stressScore = scores.stressScore,
                speedScore = scores.speedScore,
                audioIssues = scores.audioIssues,
                analysisSource = scores.analysisSource,
                detailSourceNote = scores.detailSourceNote,
                guidanceText = null
            )
        }.getOrElse { error ->
            mapError(traceId, error)
        }
    }

    private fun mapError(traceId: String, throwable: Throwable): SpeechResult {
        return when (val error = throwable.toBaiduClientException()) {
            is BaiduAuthException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.AuthFailure(error.message),
                message = error.message,
                causeCode = error.code
            )

            is BaiduNetworkException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.NetworkFailure(error.message),
                message = error.message
            )

            is BaiduApiException -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.ProviderFailure(error.message),
                message = error.message,
                causeCode = error.code
            )

            else -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unknown(error.message),
                message = error.message
            )
        }
    }
}

private fun ShadowingEvaluateResponseDto.toSpeechResult(
    provider: SpeechProviderType,
    traceId: String
): ShadowingEvaluationResult {
    return ShadowingEvaluationResult(
        provider = provider,
        traceId = traceId,
        totalScore = totalScore.coerceIn(0, 100),
        pronunciationScore = pronunciationScore.coerceIn(0, 100),
        fluencyScore = fluencyScore.coerceIn(0, 100),
        recognizedText = recognizedText,
        intonationScore = intonationScore?.coerceIn(0, 100),
        stressScore = stressScore?.coerceIn(0, 100),
        speedScore = speedScore?.coerceIn(0, 100),
        audioIssues = audioIssues.orEmpty().mapNotNull { it.toDomainIssue() },
        analysisSource = analysisSource.toAnalysisSource(),
        detailSourceNote = detailSourceNote,
        guidanceText = guidanceText
    )
}

private fun String?.toAnalysisSource(): ShadowingAnalysisSource {
    return when (this?.trim()?.uppercase()) {
        ShadowingAnalysisSource.LOCAL_PLACEHOLDER.name -> ShadowingAnalysisSource.LOCAL_PLACEHOLDER
        ShadowingAnalysisSource.PROVIDER_PLUS_LOCAL.name -> ShadowingAnalysisSource.PROVIDER_PLUS_LOCAL
        else -> ShadowingAnalysisSource.PROVIDER_ONLY
    }
}

private fun ShadowingAudioIssueDto.toDomainIssue(): ShadowingAudioIssue? {
    val issueType = when (type?.trim()?.uppercase()) {
        ShadowingAudioIssueType.LOW_VOLUME.name -> ShadowingAudioIssueType.LOW_VOLUME
        ShadowingAudioIssueType.MOSTLY_SILENT.name -> ShadowingAudioIssueType.MOSTLY_SILENT
        ShadowingAudioIssueType.TOO_FAST.name -> ShadowingAudioIssueType.TOO_FAST
        ShadowingAudioIssueType.TOO_SLOW.name -> ShadowingAudioIssueType.TOO_SLOW
        ShadowingAudioIssueType.ENVIRONMENT_NOISE.name -> ShadowingAudioIssueType.ENVIRONMENT_NOISE
        else -> null
    } ?: return null
    val issueSeverity = when (severity?.trim()?.uppercase()) {
        ShadowingAudioIssueSeverity.WARNING.name -> ShadowingAudioIssueSeverity.WARNING
        else -> ShadowingAudioIssueSeverity.INFO
    }
    return ShadowingAudioIssue(
        type = issueType,
        severity = issueSeverity,
        message = message?.takeIf { it.isNotBlank() }
    )
}
