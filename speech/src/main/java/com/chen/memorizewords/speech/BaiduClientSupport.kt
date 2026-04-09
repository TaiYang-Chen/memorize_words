package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechAudioFormat
import java.io.IOException
import java.util.Locale

internal open class BaiduClientException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

internal class BaiduAuthException(
    message: String,
    cause: Throwable? = null,
    val code: String? = null
) : BaiduClientException(message, cause)

internal class BaiduNetworkException(
    message: String,
    cause: Throwable? = null
) : BaiduClientException(message, cause)

internal class BaiduApiException(
    message: String,
    val code: String? = null
) : BaiduClientException(message)

internal data class BaiduAccessToken(
    val token: String,
    val expiresAtMillis: Long
) {
    fun isValid(nowMillis: Long): Boolean = token.isNotBlank() && nowMillis < expiresAtMillis
}

internal data class BaiduRecognizedSpeech(
    val recognizedText: String,
    val rawJson: String
)

internal fun Throwable.toBaiduClientException(): BaiduClientException {
    return when (this) {
        is BaiduClientException -> this
        is IOException -> BaiduNetworkException(message ?: "Baidu network request failed.", this)
        else -> BaiduClientException(message ?: "Baidu request failed.", this)
    }
}

internal fun baiduLanguageTag(locale: String): String {
    return if (locale.lowercase(Locale.US).startsWith("zh")) "zh" else "en"
}

internal fun baiduVoicePerson(voice: String, locale: String): String {
    val normalizedVoice = voice.trim().lowercase(Locale.US)
    if (normalizedVoice.toIntOrNull() != null) {
        return normalizedVoice
    }
    return if (baiduLanguageTag(locale) == "zh") {
        when (normalizedVoice) {
            "male" -> "1"
            else -> "0"
        }
    } else {
        when (normalizedVoice) {
            "male" -> "4106"
            else -> "4100"
        }
    }
}

internal fun baiduAudioEncoding(format: SpeechAudioFormat): String {
    val encoding = format.encoding.lowercase(Locale.US)
    val mimeType = format.mimeType.lowercase(Locale.US)
    return when {
        encoding.contains("pcm") || mimeType.contains("pcm") -> "pcm"
        encoding.contains("wav") || mimeType.contains("wav") -> "wav"
        encoding.contains("aac") || mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        else -> "mp3"
    }
}

internal fun baiduAue(format: SpeechAudioFormat): String {
    return when (baiduAudioEncoding(format)) {
        "pcm" -> "4"
        "wav" -> "6"
        else -> "3"
    }
}
