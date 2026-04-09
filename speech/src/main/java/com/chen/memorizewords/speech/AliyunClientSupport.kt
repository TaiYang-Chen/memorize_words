package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechAudioFormat
import java.io.IOException
import java.util.Locale

internal open class AliyunClientException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

internal class AliyunAuthException(
    message: String,
    cause: Throwable? = null,
    val code: String? = null
) : AliyunClientException(message, cause)

internal class AliyunNetworkException(
    message: String,
    cause: Throwable? = null
) : AliyunClientException(message, cause)

internal class AliyunApiException(
    message: String,
    val code: String? = null
) : AliyunClientException(message)

internal fun Throwable.toAliyunClientException(): AliyunClientException {
    return when (this) {
        is AliyunClientException -> this
        is IOException -> AliyunNetworkException(message ?: "Aliyun network request failed.", this)
        else -> AliyunClientException(message ?: "Aliyun request failed.", this)
    }
}

internal fun aliyunFormat(format: SpeechAudioFormat): String {
    val encoding = format.encoding.lowercase(Locale.US)
    val mimeType = format.mimeType.lowercase(Locale.US)
    return when {
        encoding.contains("wav") || mimeType.contains("wav") -> "wav"
        encoding.contains("pcm") || mimeType.contains("pcm") -> "pcm"
        else -> "mp3"
    }
}
