package com.chen.memorizewords.speech

import android.content.Context
import android.provider.Settings
import com.chen.memorizewords.speech.api.SpeechAudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class BaiduTtsClient @Inject constructor(
    @BaiduHttpClient private val httpClient: OkHttpClient,
    private val authProvider: BaiduAuthProvider,
    private val speechCacheStore: SpeechCacheStore,
    private val runtimeConfig: SpeechRuntimeConfig,
    @ApplicationContext private val context: Context
) {

    internal suspend fun synthesize(task: SpeechSynthesisTask, traceId: String): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BAIDU_TTS_URL)
            .post(
                FormBody.Builder()
                    .add("tex", task.text)
                    .add("tok", authProvider.accessToken())
                    .add("cuid", buildDeviceId())
                    .add("ctp", "1")
                    .add("lan", baiduLanguageTag(task.locale))
                    .add("spd", "5")
                    .add("pit", "5")
                    .add("vol", "9")
                    .add("per", baiduVoicePerson(task.voice, task.locale))
                    .add("aue", baiduAue(task.audioFormat))
                    .build()
            )
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type").orEmpty()
                if (response.isSuccessful && contentType.startsWith("audio/")) {
                    val target = speechCacheStore.createTempFile("baidu_tts_$traceId")
                    target.writeBytes(bodyBytes)
                    target
                } else {
                    val payload = bodyBytes.toString(Charsets.UTF_8)
                    throw parseBaiduApiException(
                        payload = payload,
                        fallbackMessage = "Baidu TTS request failed.",
                        fallbackCode = response.code.toString()
                    )
                }
            }
        }.getOrElse { throw it.toBaiduClientException() }
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

    private companion object {
        const val BAIDU_TTS_URL = "https://tsn.baidu.com/text2audio"
    }
}

internal data class SpeechSynthesisTask(
    val text: String,
    val locale: String,
    val voice: String,
    val audioFormat: SpeechAudioFormat
)
