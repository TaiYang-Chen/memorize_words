package com.chen.memorizewords.speech

import android.util.Log
import com.chen.memorizewords.core.network.http.NetworkResult
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
import com.chen.memorizewords.speech.remoteapi.api.practice.PracticeSpeechRequest
import com.chen.memorizewords.speech.remoteapi.api.practice.TtsRequestDto
import com.chen.memorizewords.speech.remoteapi.api.practice.ShadowingAudioIssueDto
import com.chen.memorizewords.speech.remoteapi.api.practice.ShadowingEvaluateResponseDto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class BaiduSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val practiceSpeechRequest: PracticeSpeechRequest,
    @BaiduHttpClient private val httpClient: OkHttpClient,
    private val networkConfig: com.chen.memorizewords.core.network.CoreNetworkConfig
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.BAIDU
    override val capabilities: Set<SpeechCapability> = setOf(
        SpeechCapability.SENTENCE_TTS
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

            is SpeechTask.SynthesizeWord -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unsupported("Baidu is reserved for sentence TTS in this app."),
                message = "Baidu is reserved for sentence TTS in this app."
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
            val backendAudio = synthesizeViaBackend(
                text = text,
                locale = locale,
                voice = voice,
                audioFormat = audioFormat,
                traceId = traceId
            )
            val output = SpeechAudioOutput.FileOutput(
                filePath = backendAudio.file.absolutePath,
                format = audioFormat
            )
            val cacheKey = backendAudio.cacheKey ?: speechCacheStore.stableHash(task.cacheDescriptor(provider.name))
            when (task) {
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

    private suspend fun synthesizeViaBackend(
        text: String,
        locale: String,
        voice: String,
        audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat,
        traceId: String
    ): BackendAudioFile {
        val remoteResult = practiceSpeechRequest.synthesize(
            TtsRequestDto(
                text = text,
                language = baiduLanguageTag(locale),
                voice = voice,
                provider = provider.name,
                speed = 5,
                pitch = 5,
                volume = 9,
                audioFormat = baiduAue(audioFormat)
            )
        )
        when (remoteResult) {
            is NetworkResult.Success -> {
                val response = remoteResult.data
                val audioUrl = response.audioUrl?.takeIf { it.isNotBlank() }
                    ?: throw BaiduApiException("Backend TTS response does not contain audioUrl.")
                return BackendAudioFile(
                    file = downloadBackendAudio(audioUrl, traceId, audioFormat),
                    cacheKey = response.cacheKey
                )
            }

            is NetworkResult.Failure -> throw remoteResult.toBackendTtsException()
        }
    }

    private suspend fun downloadBackendAudio(
        audioUrl: String,
        traceId: String,
        audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat
    ): File = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveBackendAudioUrl(audioUrl)
        val request = Request.Builder().url(resolvedUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw BaiduNetworkException("Backend TTS audio download failed: HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) {
                throw BaiduApiException("Backend TTS audio is empty.")
            }
            speechCacheStore.createTempFile("backend_baidu_tts_$traceId", audioFormat).also { file ->
                file.writeBytes(bytes)
            }
        }
    }

    private data class BackendAudioFile(
        val file: File,
        val cacheKey: String?
    )

    private fun resolveBackendAudioUrl(audioUrl: String): String {
        if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
            return audioUrl
        }
        val base = networkConfig.baseUrl.toHttpUrlOrNull()
            ?: throw BaiduApiException("Backend base URL is invalid.")
        return base.resolve(audioUrl)?.toString()
            ?: throw BaiduApiException("Backend TTS audioUrl is invalid.")
    }

    private suspend fun evaluate(task: SpeechTask.EvaluateShadowing, traceId: String): SpeechResult {
        val normalizedInput = when (val input = task.audioInput) {
            is SpeechAudioInput.FileInput -> input
            is SpeechAudioInput.ByteArrayInput -> {
                val tempFile = speechCacheStore.createTempFile("shadowing_$traceId", input.format)
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
            requestId = task.requestId,
            referenceText = task.referenceText,
            provider = provider.name,
            audioFilePath = normalizedInput.filePath,
            audioFormat = normalizedInput.format.encoding,
            durationMs = task.recordingMetadata.durationMs,
            waveformSamples = task.recordingMetadata.waveformSamples
        )
        if (remoteResult is NetworkResult.Success) {
            return remoteResult.data.toSpeechResult(
                provider = provider,
                traceId = traceId
            )
        }
        return failureResult(
            provider = provider,
            traceId = traceId,
            failure = SpeechFailure.ProviderFailure("Backend shadowing evaluate request failed."),
            message = "Backend shadowing evaluate request failed."
        )
    }

    private fun mapError(traceId: String, throwable: Throwable): SpeechResult {
        Log.w(TAG, "Baidu speech request failed. traceId=$traceId", throwable)
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

private const val TAG = "BaiduSpeechProvider"

private fun NetworkResult.Failure.toBackendTtsException(): BaiduClientException {
    return when (this) {
        is NetworkResult.Failure.Unauthorized -> BaiduAuthException(
            message = message ?: "Backend TTS request unauthorized.",
            code = code.toString()
        )

        is NetworkResult.Failure.HttpError -> BaiduApiException(
            message = message ?: "Backend TTS request failed: HTTP $code",
            code = businessCode ?: code.toString()
        )

        is NetworkResult.Failure.NetworkError -> BaiduNetworkException(
            message = throwable.message ?: "Backend TTS network request failed.",
            cause = throwable
        )

        is NetworkResult.Failure.GenericError -> BaiduApiException(message)
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
