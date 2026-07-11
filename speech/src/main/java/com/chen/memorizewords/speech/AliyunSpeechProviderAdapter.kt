package com.chen.memorizewords.speech

import android.util.Log
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechCapability
import com.chen.memorizewords.speech.api.SpeechFailure
import com.chen.memorizewords.speech.api.SpeechProviderAdapter
import com.chen.memorizewords.speech.api.SpeechProviderType
import com.chen.memorizewords.speech.api.SpeechResult
import com.chen.memorizewords.speech.api.SpeechTask
import com.chen.memorizewords.speech.api.WordAudioResult
import com.chen.memorizewords.speech.remoteapi.api.practice.PracticeSpeechRequest
import com.chen.memorizewords.speech.remoteapi.api.practice.TtsRequestDto
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AliyunSpeechProviderAdapter @Inject constructor(
    private val speechCacheStore: SpeechCacheStore,
    private val practiceSpeechRequest: PracticeSpeechRequest,
    @BaiduHttpClient private val httpClient: OkHttpClient,
    private val networkConfig: com.chen.memorizewords.core.network.CoreNetworkConfig
) : SpeechProviderAdapter {

    override val provider: SpeechProviderType = SpeechProviderType.ALIYUN
    override val capabilities: Set<SpeechCapability> = setOf(SpeechCapability.WORD_TTS)

    override suspend fun execute(task: SpeechTask, traceId: String): SpeechResult {
        return when (task) {
            is SpeechTask.SynthesizeWord -> synthesizeWord(task, traceId)
            else -> failureResult(
                provider = provider,
                traceId = traceId,
                failure = SpeechFailure.Unsupported("Aliyun only supports word TTS in this app."),
                message = "Aliyun only supports word TTS in this app."
            )
        }
    }

    private suspend fun synthesizeWord(
        task: SpeechTask.SynthesizeWord,
        traceId: String
    ): SpeechResult {
        return runCatching {
            val remoteResult = practiceSpeechRequest.synthesize(
                TtsRequestDto(
                    text = task.text,
                    language = task.locale,
                    voice = task.voice,
                    provider = provider.name,
                    speed = 0,
                    pitch = 0,
                    volume = 50,
                    audioFormat = aliyunFormat(task.audioFormat)
                )
            )
            val response = when (remoteResult) {
                is NetworkResult.Success -> remoteResult.data
                is NetworkResult.Failure -> throw remoteResult.toBackendTtsException()
            }
            val audioUrl = response.audioUrl?.takeIf { it.isNotBlank() }
                ?: throw BaiduApiException("Backend Aliyun TTS response does not contain audioUrl.")
            val output = SpeechAudioOutput.FileOutput(
                filePath = downloadBackendAudio(audioUrl, traceId, task.audioFormat).absolutePath,
                format = task.audioFormat
            )
            WordAudioResult(
                provider = provider,
                traceId = traceId,
                audioOutput = output,
                cacheKey = response.cacheKey ?: speechCacheStore.stableHash(task.cacheDescriptor(provider.name)),
                isFromCache = false
            )
        }.getOrElse { error ->
            Log.w(TAG, "Aliyun word TTS request failed. traceId=$traceId", error)
            val mapped = error.toBaiduClientException()
            failureResult(
                provider = provider,
                traceId = traceId,
                failure = when (mapped) {
                    is BaiduAuthException -> SpeechFailure.AuthFailure(mapped.message)
                    is BaiduNetworkException -> SpeechFailure.NetworkFailure(mapped.message)
                    is BaiduApiException -> SpeechFailure.ProviderFailure(mapped.message)
                    else -> SpeechFailure.Unknown(mapped.message)
                },
                message = mapped.message,
                causeCode = (mapped as? BaiduApiException)?.code ?: (mapped as? BaiduAuthException)?.code
            )
        }
    }

    private suspend fun downloadBackendAudio(
        audioUrl: String,
        traceId: String,
        audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(resolveBackendAudioUrl(audioUrl)).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw BaiduNetworkException("Backend Aliyun TTS audio download failed: HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) {
                throw BaiduApiException("Backend Aliyun TTS audio is empty.")
            }
            speechCacheStore.createTempFile("backend_aliyun_tts_$traceId", audioFormat).also { file ->
                file.writeBytes(bytes)
            }
        }
    }

    private fun resolveBackendAudioUrl(audioUrl: String): String {
        if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
            return audioUrl
        }
        val base = networkConfig.baseUrl.toHttpUrlOrNull()
            ?: throw BaiduApiException("Backend base URL is invalid.")
        return base.resolve(audioUrl)?.toString()
            ?: throw BaiduApiException("Backend Aliyun TTS audioUrl is invalid.")
    }
}

private const val TAG = "AliyunSpeechProvider"

private fun aliyunFormat(format: com.chen.memorizewords.speech.api.SpeechAudioFormat): String {
    val encoding = format.encoding.lowercase()
    val mimeType = format.mimeType.lowercase()
    return when {
        encoding.contains("wav") || mimeType.contains("wav") -> "wav"
        encoding.contains("pcm") || mimeType.contains("pcm") -> "pcm"
        else -> "mp3"
    }
}

private fun NetworkResult.Failure.toBackendTtsException(): BaiduClientException {
    return when (this) {
        is NetworkResult.Failure.Unauthorized -> BaiduAuthException(
            message = message ?: "Backend Aliyun TTS request unauthorized.",
            code = code.toString()
        )

        is NetworkResult.Failure.HttpError -> BaiduApiException(
            message = message ?: "Backend Aliyun TTS request failed: HTTP $code",
            code = businessCode ?: code.toString()
        )

        is NetworkResult.Failure.NetworkError -> BaiduNetworkException(
            message = throwable.message ?: "Backend Aliyun TTS network request failed.",
            cause = throwable
        )

        is NetworkResult.Failure.GenericError -> BaiduApiException(message)
    }
}
