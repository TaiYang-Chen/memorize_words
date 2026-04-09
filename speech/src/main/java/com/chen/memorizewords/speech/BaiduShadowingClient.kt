package com.chen.memorizewords.speech

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.chen.memorizewords.speech.api.SpeechAudioFormat
import com.chen.memorizewords.speech.api.SpeechAudioInput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class BaiduShadowingClient @Inject constructor(
    @BaiduHttpClient private val httpClient: OkHttpClient,
    private val authProvider: BaiduAuthProvider,
    private val runtimeConfig: SpeechRuntimeConfig,
    @ApplicationContext private val context: Context
) {

    internal suspend fun recognize(audioInput: SpeechAudioInput, locale: String): BaiduRecognizedSpeech =
        withContext(Dispatchers.IO) {
            val requestAudio = audioRequest(audioInput)
            val payload = JSONObject().apply {
                put("format", requestAudio.format)
                put("rate", requestAudio.sampleRateHz)
                put("channel", requestAudio.channelCount)
                put("cuid", buildDeviceId())
                put("token", authProvider.accessToken())
                put("dev_pid", baiduDevPid(locale))
                put("speech", Base64.encodeToString(requestAudio.bytes, Base64.NO_WRAP))
                put("len", requestAudio.bytes.size)
            }
            val request = Request.Builder()
                .url(BAIDU_ASR_URL)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw parseBaiduApiException(
                            payload = body,
                            fallbackMessage = "Baidu ASR request failed.",
                            fallbackCode = response.code.toString()
                        )
                    }
                    parseRecognition(body)
                }
            }.getOrElse { throw it.toBaiduClientException() }
        }

    private fun audioRequest(input: SpeechAudioInput): BaiduAudioRequest {
        return when (input) {
            is SpeechAudioInput.ByteArrayInput -> BaiduAudioRequest(
                bytes = input.bytes,
                format = inferAudioFormat(format = input.format, file = null),
                sampleRateHz = input.format.sampleRateHz,
                channelCount = input.format.channelCount
            )

            is SpeechAudioInput.FileInput -> {
                val file = File(input.filePath)
                BaiduAudioRequest(
                    bytes = file.readBytes(),
                    format = inferAudioFormat(format = input.format, file = file),
                    sampleRateHz = input.format.sampleRateHz,
                    channelCount = input.format.channelCount
                )
            }

            is SpeechAudioInput.StreamInput -> {
                throw BaiduApiException("Stream audio input is not supported.", "UNSUPPORTED_STREAM")
            }
        }
    }

    private fun parseRecognition(payload: String): BaiduRecognizedSpeech {
        val json = JSONObject(payload.ifBlank { "{}" })
        val errNo = json.optInt("err_no", 0)
        if (errNo != 0) {
            throw parseBaiduApiException(
                payload = payload,
                fallbackMessage = json.optString("err_msg").ifBlank { "Baidu ASR returned an error." },
                fallbackCode = errNo.toString()
            )
        }
        val results = json.optJSONArray("result") ?: JSONArray()
        val recognizedText = if (results.length() > 0) {
            results.optString(0).trim()
        } else {
            ""
        }
        return BaiduRecognizedSpeech(
            recognizedText = recognizedText,
            rawJson = payload
        )
    }

    private fun inferAudioFormat(format: SpeechAudioFormat, file: File?): String {
        val extension = file?.extension?.lowercase().orEmpty()
        if (extension.isNotBlank()) {
            return when (extension) {
                "m4a", "mp4" -> "m4a"
                "wav" -> "wav"
                "pcm" -> "pcm"
                "amr" -> "amr"
                "mp3" -> "mp3"
                else -> baiduAudioEncoding(format)
            }
        }
        return baiduAudioEncoding(format)
    }

    private fun buildDeviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return listOf(
            runtimeConfig.baiduAppId.takeIf { it.isNotBlank() },
            androidId.takeIf { it.isNotBlank() },
            context.packageName
        ).joinToString(separator = "_")
    }

    private fun baiduDevPid(locale: String): Int {
        return if (locale.lowercase().startsWith("zh")) 1537 else 1737
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val BAIDU_ASR_URL = "https://vop.baidu.com/server_api"
    }
}

internal data class BaiduAudioRequest(
    val bytes: ByteArray,
    val format: String,
    val sampleRateHz: Int,
    val channelCount: Int
)

internal fun parseBaiduApiException(
    payload: String,
    fallbackMessage: String,
    fallbackCode: String? = null
): BaiduClientException {
    return runCatching {
        val json = JSONObject(payload.ifBlank { "{}" })
        val code = json.optString("err_no").ifBlank {
            json.optString("error_code").ifBlank {
                json.optString("error").ifBlank { fallbackCode.orEmpty() }
            }
        }.ifBlank { fallbackCode }
        val message = json.optString("err_msg").ifBlank {
            json.optString("error_msg").ifBlank {
                json.optString("error_description").ifBlank {
                    json.optString("error").ifBlank { fallbackMessage }
                }
            }
        }
        if (code == "110" || code == "111" || code == "3300" || code == "3301" || code == "3302") {
            BaiduAuthException(message = message, code = code)
        } else {
            BaiduApiException(message = message, code = code)
        }
    }.getOrElse {
        BaiduApiException(fallbackMessage, fallbackCode)
    }
}
