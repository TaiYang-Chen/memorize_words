package com.chen.memorizewords.speech

import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.speech.api.DefaultSpeechFailureResult
import com.chen.memorizewords.speech.api.ShadowingAnalysisSource
import com.chen.memorizewords.speech.api.ShadowingAudioIssue
import com.chen.memorizewords.speech.api.ShadowingAudioIssueSeverity
import com.chen.memorizewords.speech.api.ShadowingAudioIssueType
import com.chen.memorizewords.speech.api.ShadowingDetail
import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.ShadowingRecordingQuality
import com.chen.memorizewords.speech.api.SpeechAudioInput
import com.chen.memorizewords.speech.api.SpeechCapability
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechProviderAdapter
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.remoteapi.api.practice.RecordingQualityDto
import com.chen.memorizewords.speech.remoteapi.api.practice.ShadowingAudioIssueDto
import com.chen.memorizewords.speech.remoteapi.api.practice.ShadowingDetailDto
import com.chen.memorizewords.speech.remoteapi.api.practice.ShadowingEvaluateResponseDto
import com.chen.memorizewords.speech.remoteapi.api.practice.PracticeSpeechRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XunfeiSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val practiceSpeechRequest: PracticeSpeechRequest
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.XUNFEI
    override val capabilities: Set<SpeechCapability> = setOf(SpeechCapability.SHADOWING_EVALUATION)

    override suspend fun execute(task: SpeechTask, traceId: String): SpeechResult {
        return when (task) {
            is SpeechTask.EvaluateShadowing -> evaluate(task, traceId)
            else -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unsupported("Xunfei only supports shadowing evaluation in this app."),
                message = "Xunfei only supports shadowing evaluation in this app."
            )
        }
    }

    private suspend fun evaluate(task: SpeechTask.EvaluateShadowing, traceId: String): SpeechResult {
        val normalizedInput = when (val input = task.audioInput) {
            is SpeechAudioInput.FileInput -> input
            is SpeechAudioInput.ByteArrayInput -> {
                val tempFile = speechCacheStore.createTempFile("shadowing_xunfei_$traceId", input.format)
                tempFile.writeBytes(input.bytes)
                SpeechAudioInput.FileInput(tempFile.absolutePath, input.format)
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
        if (!File(normalizedInput.filePath).exists()) {
            return failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.InvalidRequest("Audio file not found."),
                message = "Audio file not found."
            )
        }
        val remoteResult = practiceSpeechRequest.evaluateShadowing(
            referenceText = task.referenceText,
            provider = provider.name,
            audioFilePath = normalizedInput.filePath,
            audioFormat = normalizedInput.format.encoding,
            durationMs = task.recordingMetadata.durationMs,
            waveformSamples = task.recordingMetadata.waveformSamples
        )
        return when (remoteResult) {
            is NetworkResult.Success -> remoteResult.data.toSpeechResult(provider, traceId)
            is NetworkResult.Failure -> DefaultSpeechFailureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.ProviderFailure("Backend Xunfei evaluate request failed."),
                message = remoteResult.messageOrDefault()
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
        accuracyScore = accuracyScore?.coerceIn(0, 100),
        standardScore = standardScore?.coerceIn(0, 100),
        audioIssues = audioIssues.orEmpty().mapNotNull { it.toDomainIssue() },
        phoneDetails = phoneDetails.orEmpty().map { it.toDomainDetail() },
        syllableDetails = syllableDetails.orEmpty().map { it.toDomainDetail() },
        wordDetails = wordDetails.orEmpty().map { it.toDomainDetail() },
        recordingQuality = recordingQuality?.toDomainQuality(),
        rawProviderTraceId = rawProviderTraceId,
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
    return ShadowingAudioIssue(issueType, issueSeverity, message?.takeIf { it.isNotBlank() })
}

private fun ShadowingDetailDto.toDomainDetail(): ShadowingDetail {
    return ShadowingDetail(
        text = text.orEmpty(),
        score = score,
        expected = expected,
        actual = actual,
        issueType = issueType,
        message = message
    )
}

private fun RecordingQualityDto.toDomainQuality(): ShadowingRecordingQuality {
    return ShadowingRecordingQuality(
        volumeScore = volumeScore,
        speechRatio = speechRatio,
        durationMs = durationMs,
        level = level,
        message = message
    )
}

private fun NetworkResult.Failure.messageOrDefault(): String {
    return when (this) {
        is NetworkResult.Failure.Unauthorized -> message ?: "Backend Xunfei request unauthorized."
        is NetworkResult.Failure.HttpError -> message ?: "Backend Xunfei request failed: HTTP $code"
        is NetworkResult.Failure.NetworkError -> throwable.message ?: "Backend Xunfei network request failed."
        is NetworkResult.Failure.GenericError -> message
    }
}
