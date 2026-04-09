package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechAudioFormat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class AliyunTtsClient @Inject constructor(
    @AliyunHttpClient private val httpClient: OkHttpClient,
    private val runtimeConfig: SpeechRuntimeConfig,
    private val tokenProvider: AliyunTokenProvider,
    private val speechCacheStore: SpeechCacheStore
) {

    internal suspend fun synthesize(task: SpeechSynthesisTask, traceId: String): File = withContext(Dispatchers.IO) {
        if (runtimeConfig.aliyunAppKey.isBlank()) {
            throw AliyunAuthException("Aliyun app key is missing.")
        }
        val voice = resolveVoice(task)
        val url = ALIYUN_TTS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("appkey", runtimeConfig.aliyunAppKey)
            .addQueryParameter("text", task.text)
            .addQueryParameter("format", aliyunFormat(task.audioFormat))
            .addQueryParameter("sample_rate", task.audioFormat.sampleRateHz.toString())
            .addQueryParameter("voice", voice)
            .addQueryParameter("volume", "50")
            .addQueryParameter("speech_rate", "0")
            .addQueryParameter("pitch_rate", "0")
            .build()
        val initialToken = tokenProvider.getValidToken()
        val firstResult = runCatching { executeRequest(url = url, token = initialToken, traceId = traceId) }
        firstResult.getOrElse { firstError ->
            val clientError = firstError.toAliyunClientException()
            if (!shouldRefreshAliyunToken(clientError)) {
                throw clientError
            }
            val refreshedToken = tokenProvider.getValidToken(forceRefresh = true)
            runCatching {
                executeRequest(url = url, token = refreshedToken, traceId = traceId)
            }.getOrElse { throw it.toAliyunClientException() }
        }
    }

    private fun resolveVoice(task: SpeechSynthesisTask): String {
        if (task.voice != "default" && task.voice.isNotBlank()) {
            return task.voice
        }
        val isEnglish = task.locale.lowercase().startsWith("en")
        return if (isEnglish) {
            runtimeConfig.aliyunWordVoice.ifBlank { DEFAULT_ENGLISH_VOICE }
        } else {
            runtimeConfig.aliyunSentenceVoice.ifBlank { DEFAULT_CHINESE_VOICE }
        }
    }

    private companion object {
        const val ALIYUN_TTS_URL = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts"
        const val DEFAULT_ENGLISH_VOICE = "ava"
        const val DEFAULT_CHINESE_VOICE = "xiaoyun"
    }

    private fun executeRequest(
        url: okhttp3.HttpUrl,
        token: String,
        traceId: String
    ): File {
        val request = Request.Builder()
            .url(url)
            .header("X-NLS-Token", token)
            .get()
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type").orEmpty()
                if (response.isSuccessful && contentType.startsWith("audio/")) {
                    val target = speechCacheStore.createTempFile("aliyun_tts_$traceId")
                    target.writeBytes(bodyBytes)
                    target
                } else {
                    throw parseAliyunApiException(
                        payload = bodyBytes.toString(Charsets.UTF_8),
                        fallbackMessage = "Aliyun TTS request failed.",
                        fallbackCode = response.code.toString()
                    )
                }
            }
        }.getOrElse { throw it.toAliyunClientException() }
    }
}

internal fun parseAliyunApiException(
    payload: String,
    fallbackMessage: String,
    fallbackCode: String? = null
): AliyunClientException {
    return runCatching {
        val json = JSONObject(payload.ifBlank { "{}" })
        val code = json.optString("status").ifBlank {
            json.optString("code").ifBlank { fallbackCode.orEmpty() }
        }.ifBlank { fallbackCode }
        val message = json.optString("message").ifBlank {
            json.optString("Message").ifBlank { fallbackMessage }
        }
        if (isAliyunTokenFailureCode(code) || isAliyunTokenFailureMessage(message)) {
            AliyunAuthException(message = message, code = code)
        } else {
            AliyunApiException(message = message, code = code)
        }
    }.getOrElse {
        AliyunApiException(fallbackMessage, fallbackCode)
    }
}

internal fun shouldRefreshAliyunToken(error: AliyunClientException): Boolean {
    return error is AliyunAuthException &&
        (isAliyunTokenFailureCode(error.code) || isAliyunTokenFailureMessage(error.message.orEmpty()))
}

private fun isAliyunTokenFailureCode(code: String?): Boolean {
    return code == "401" ||
        code == "40000001" ||
        code == "InvalidToken" ||
        code == "TokenExpired"
}

private fun isAliyunTokenFailureMessage(message: String): Boolean {
    return message.contains("InvalidToken", ignoreCase = true) ||
        message.contains("TokenExpired", ignoreCase = true)
}
