package com.chen.memorizewords.speech

import com.chen.memorizewords.domain.practice.speech.DefaultSpeechFailureResult as DomainDefaultSpeechFailureResult
import com.chen.memorizewords.domain.practice.speech.PracticeSpeechSynthesizer
import com.chen.memorizewords.domain.practice.speech.SentenceAudioResult as DomainSentenceAudioResult
import com.chen.memorizewords.domain.practice.speech.ShadowingAnalysisSource as DomainShadowingAnalysisSource
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssue as DomainShadowingAudioIssue
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueSeverity as DomainShadowingAudioIssueSeverity
import com.chen.memorizewords.domain.practice.speech.ShadowingAudioIssueType as DomainShadowingAudioIssueType
import com.chen.memorizewords.domain.practice.speech.ShadowingDetail as DomainShadowingDetail
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluationResult as DomainShadowingEvaluationResult
import com.chen.memorizewords.domain.practice.speech.ShadowingRecordingQuality as DomainShadowingRecordingQuality
import com.chen.memorizewords.domain.practice.speech.ShadowingEvaluator
import com.chen.memorizewords.domain.practice.speech.ShadowingRecordingMetadata as DomainShadowingRecordingMetadata
import com.chen.memorizewords.domain.practice.speech.SpeechAudioFormat as DomainSpeechAudioFormat
import com.chen.memorizewords.domain.practice.speech.SpeechAudioInput as DomainSpeechAudioInput
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput as DomainSpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechFailure as DomainSpeechFailure
import com.chen.memorizewords.domain.practice.speech.SpeechFailureResult as DomainSpeechFailureResult
import com.chen.memorizewords.domain.practice.speech.SpeechProviderType as DomainSpeechProviderType
import com.chen.memorizewords.domain.practice.speech.SpeechResult as DomainSpeechResult
import com.chen.memorizewords.domain.practice.speech.SpeechTask as DomainSpeechTask
import com.chen.memorizewords.domain.practice.speech.WordAudioResult as DomainWordAudioResult
import com.chen.memorizewords.speech.api.DefaultSpeechFailureResult
import com.chen.memorizewords.speech.api.SentenceAudioResult
import com.chen.memorizewords.speech.api.ShadowingAnalysisSource
import com.chen.memorizewords.speech.api.ShadowingAudioIssue
import com.chen.memorizewords.speech.api.ShadowingAudioIssueSeverity
import com.chen.memorizewords.speech.api.ShadowingAudioIssueType
import com.chen.memorizewords.speech.api.ShadowingDetail
import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.ShadowingRecordingQuality
import com.chen.memorizewords.speech.api.ShadowingRecordingMetadata
import com.chen.memorizewords.speech.api.SpeechAudioFormat
import com.chen.memorizewords.speech.api.SpeechAudioInput
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechFailureResult
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechService
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.api.WordAudioResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PracticeSpeechDomainAdapter @Inject constructor(
    private val speechService: SpeechService
) : PracticeSpeechSynthesizer, ShadowingEvaluator {

    override suspend fun synthesize(task: DomainSpeechTask): DomainSpeechResult {
        return speechService.execute(task.toApi()).toDomain()
    }

    override suspend fun evaluate(task: DomainSpeechTask.EvaluateShadowing): DomainSpeechResult {
        return speechService.execute(task.toApi()).toDomain()
    }
}

private fun DomainSpeechTask.toApi(): SpeechTask {
    return when (this) {
        is DomainSpeechTask.SynthesizeWord -> SpeechTask.SynthesizeWord(
            text = text,
            locale = locale,
            voice = voice,
            audioFormat = audioFormat.toApi()
        )

        is DomainSpeechTask.SynthesizeSentence -> SpeechTask.SynthesizeSentence(
            text = text,
            locale = locale,
            voice = voice,
            audioFormat = audioFormat.toApi()
        )

        is DomainSpeechTask.EvaluateShadowing -> SpeechTask.EvaluateShadowing(
            referenceText = referenceText,
            audioInput = audioInput.toApi(),
            locale = locale,
            recordingMetadata = recordingMetadata.toApi()
        )
    }
}

private fun SpeechResult.toDomain(): DomainSpeechResult {
    return when (this) {
        is WordAudioResult -> DomainWordAudioResult(
            provider = provider.toDomain(),
            traceId = traceId,
            audioOutput = audioOutput.toDomain(),
            cacheKey = cacheKey,
            isFromCache = isFromCache
        )

        is SentenceAudioResult -> DomainSentenceAudioResult(
            provider = provider.toDomain(),
            traceId = traceId,
            audioOutput = audioOutput.toDomain(),
            cacheKey = cacheKey,
            isFromCache = isFromCache
        )

        is ShadowingEvaluationResult -> DomainShadowingEvaluationResult(
            provider = provider.toDomain(),
            traceId = traceId,
            totalScore = totalScore,
            pronunciationScore = pronunciationScore,
            fluencyScore = fluencyScore,
            recognizedText = recognizedText,
            intonationScore = intonationScore,
            stressScore = stressScore,
            speedScore = speedScore,
            accuracyScore = accuracyScore,
            standardScore = standardScore,
            audioIssues = audioIssues.map { it.toDomain() },
            phoneDetails = phoneDetails.map { it.toDomain() },
            syllableDetails = syllableDetails.map { it.toDomain() },
            wordDetails = wordDetails.map { it.toDomain() },
            recordingQuality = recordingQuality?.toDomain(),
            rawProviderTraceId = rawProviderTraceId,
            analysisSource = analysisSource.toDomain(),
            detailSourceNote = detailSourceNote,
            guidanceText = guidanceText
        )

        is DefaultSpeechFailureResult -> toDomainFailureResult()
        is SpeechFailureResult -> DomainDefaultSpeechFailureResult(
            provider = provider.toDomain(),
            traceId = traceId,
            failure = failure.toDomain(),
            causeCode = causeCode,
            message = message
        )
    }
}

private fun DefaultSpeechFailureResult.toDomainFailureResult(): DomainSpeechFailureResult {
    return DomainDefaultSpeechFailureResult(
        provider = provider.toDomain(),
        traceId = traceId,
        failure = failure.toDomain(),
        causeCode = causeCode,
        message = message
    )
}

private fun DomainSpeechAudioFormat.toApi(): SpeechAudioFormat {
    return SpeechAudioFormat(
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
        encoding = encoding
    )
}

private fun SpeechAudioFormat.toDomain(): DomainSpeechAudioFormat {
    return DomainSpeechAudioFormat(
        mimeType = mimeType,
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
        encoding = encoding
    )
}

private fun DomainSpeechAudioInput.toApi(): SpeechAudioInput {
    return when (this) {
        is DomainSpeechAudioInput.FileInput -> SpeechAudioInput.FileInput(filePath, format.toApi())
        is DomainSpeechAudioInput.ByteArrayInput -> SpeechAudioInput.ByteArrayInput(bytes, format.toApi())
        is DomainSpeechAudioInput.StreamInput -> SpeechAudioInput.StreamInput(streamId, format.toApi())
    }
}

private fun SpeechAudioOutput.toDomain(): DomainSpeechAudioOutput {
    return when (this) {
        is SpeechAudioOutput.FileOutput -> DomainSpeechAudioOutput.FileOutput(filePath, format.toDomain())
        is SpeechAudioOutput.UrlOutput -> DomainSpeechAudioOutput.UrlOutput(url, format.toDomain())
        is SpeechAudioOutput.StreamOutput -> DomainSpeechAudioOutput.StreamOutput(streamId, format.toDomain())
    }
}

private fun DomainShadowingRecordingMetadata.toApi(): ShadowingRecordingMetadata {
    return ShadowingRecordingMetadata(
        durationMs = durationMs,
        waveformSamples = waveformSamples
    )
}

private fun ShadowingAudioIssue.toDomain(): DomainShadowingAudioIssue {
    return DomainShadowingAudioIssue(
        type = type.toDomain(),
        severity = severity.toDomain(),
        message = message
    )
}

private fun SpeechProviderType.toDomain(): DomainSpeechProviderType {
    return when (this) {
        SpeechProviderType.BAIDU -> DomainSpeechProviderType.BAIDU
        SpeechProviderType.ALIYUN -> DomainSpeechProviderType.ALIYUN
        SpeechProviderType.XUNFEI -> DomainSpeechProviderType.XUNFEI
    }
}

private fun SpeechFailure.toDomain(): DomainSpeechFailure {
    return when (this) {
        is SpeechFailure.AuthFailure -> DomainSpeechFailure.AuthFailure(message)
        is SpeechFailure.NetworkFailure -> DomainSpeechFailure.NetworkFailure(message)
        is SpeechFailure.ProviderFailure -> DomainSpeechFailure.ProviderFailure(message)
        is SpeechFailure.Unsupported -> DomainSpeechFailure.Unsupported(message)
        is SpeechFailure.InvalidRequest -> DomainSpeechFailure.InvalidRequest(message)
        is SpeechFailure.Unknown -> DomainSpeechFailure.Unknown(message, causeCode)
    }
}

private fun ShadowingDetail.toDomain(): DomainShadowingDetail {
    return DomainShadowingDetail(
        text = text,
        score = score,
        expected = expected,
        actual = actual,
        issueType = issueType,
        message = message
    )
}

private fun ShadowingRecordingQuality.toDomain(): DomainShadowingRecordingQuality {
    return DomainShadowingRecordingQuality(
        volumeScore = volumeScore,
        speechRatio = speechRatio,
        durationMs = durationMs,
        level = level,
        message = message
    )
}

private fun ShadowingAnalysisSource.toDomain(): DomainShadowingAnalysisSource {
    return when (this) {
        ShadowingAnalysisSource.PROVIDER_ONLY -> DomainShadowingAnalysisSource.PROVIDER_ONLY
        ShadowingAnalysisSource.LOCAL_PLACEHOLDER -> DomainShadowingAnalysisSource.LOCAL_PLACEHOLDER
        ShadowingAnalysisSource.PROVIDER_PLUS_LOCAL -> DomainShadowingAnalysisSource.PROVIDER_PLUS_LOCAL
    }
}

private fun ShadowingAudioIssueType.toDomain(): DomainShadowingAudioIssueType {
    return when (this) {
        ShadowingAudioIssueType.LOW_VOLUME -> DomainShadowingAudioIssueType.LOW_VOLUME
        ShadowingAudioIssueType.MOSTLY_SILENT -> DomainShadowingAudioIssueType.MOSTLY_SILENT
        ShadowingAudioIssueType.TOO_FAST -> DomainShadowingAudioIssueType.TOO_FAST
        ShadowingAudioIssueType.TOO_SLOW -> DomainShadowingAudioIssueType.TOO_SLOW
        ShadowingAudioIssueType.ENVIRONMENT_NOISE -> DomainShadowingAudioIssueType.ENVIRONMENT_NOISE
    }
}

private fun ShadowingAudioIssueSeverity.toDomain(): DomainShadowingAudioIssueSeverity {
    return when (this) {
        ShadowingAudioIssueSeverity.INFO -> DomainShadowingAudioIssueSeverity.INFO
        ShadowingAudioIssueSeverity.WARNING -> DomainShadowingAudioIssueSeverity.WARNING
    }
}
