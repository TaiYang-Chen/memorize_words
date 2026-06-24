package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechTask
import java.util.Locale
import org.json.JSONObject

internal fun SpeechTask.cacheDescriptor(providerName: String): String {
    return when (this) {
        is SpeechTask.SynthesizeSentence -> synthesisCacheDescriptor(
            providerName = providerName,
            capabilityName = requiredCapability.name,
            voice = voice,
            locale = locale,
            text = text,
            audioFormat = audioFormat
        )

        is SpeechTask.SynthesizeWord -> synthesisCacheDescriptor(
            providerName = providerName,
            capabilityName = requiredCapability.name,
            voice = voice,
            locale = locale,
            text = text,
            audioFormat = audioFormat
        )

        is SpeechTask.EvaluateShadowing -> listOf(
            providerName,
            requiredCapability.name,
            locale,
            referenceText
        ).joinToString(separator = "|")
    }
}

private fun synthesisCacheDescriptor(
    providerName: String,
    capabilityName: String,
    voice: String,
    locale: String,
    text: String,
    audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat
): String {
    if (providerName.equals("BAIDU", ignoreCase = true)) {
        return JSONObject()
            .put("provider", "BAIDU")
            .put("text", text.replace("\r\n", "\n").replace('\r', '\n').trim())
            .put("lan", baiduLanguageTag(locale))
            .put("per", baiduCacheVoice(voice))
            .put("spd", 5)
            .put("pit", 5)
            .put("vol", 9)
            .put("aue", baiduAue(audioFormat))
            .put("audioCtrl", JSONObject.NULL)
            .put("textCtrl", JSONObject.NULL)
            .put("cacheSchemaVersion", 1)
            .toString()
    }
    if (providerName.equals("ALIYUN", ignoreCase = true)) {
        return JSONObject()
            .put("provider", "ALIYUN")
            .put("text", text.replace("\r\n", "\n").replace('\r', '\n').trim())
            .put("locale", locale)
            .put("voice", aliyunCacheVoice(voice, locale))
            .put("format", aliyunCacheFormat(audioFormat))
            .put("sampleRate", ALIYUN_DEFAULT_SAMPLE_RATE)
            .put("speechRate", 0)
            .put("pitchRate", 0)
            .put("volume", 50)
            .put("cacheSchemaVersion", 1)
            .toString()
    }
    return listOf(
        providerName,
        capabilityName,
        voice,
        locale,
        text,
        audioFormat.mimeType,
        audioFormat.sampleRateHz.toString(),
        audioFormat.channelCount.toString(),
        audioFormat.encoding
    ).joinToString(separator = "|")
}

private fun baiduCacheVoice(voice: String): String {
    val normalized = voice.trim()
    return if (normalized.isBlank() || normalized.equals("default", ignoreCase = true)) {
        "0"
    } else {
        normalized
    }
}

private const val ALIYUN_DEFAULT_SAMPLE_RATE = 16000

private fun aliyunCacheVoice(voice: String, locale: String): String {
    val normalized = voice.trim()
    if (normalized.isNotBlank() && !normalized.equals("default", ignoreCase = true)) {
        return normalized
    }
    return if (locale.equals("en-GB", ignoreCase = true)) {
        "Harry"
    } else {
        "Abby"
    }
}

private fun aliyunCacheFormat(
    audioFormat: com.chen.memorizewords.speech.api.SpeechAudioFormat
): String {
    val encoding = audioFormat.encoding.lowercase(Locale.US)
    val mimeType = audioFormat.mimeType.lowercase(Locale.US)
    return when {
        encoding.contains("wav") || mimeType.contains("wav") -> "wav"
        encoding.contains("pcm") || mimeType.contains("pcm") -> "pcm"
        else -> "mp3"
    }
}

internal fun localeFromTag(tag: String): Locale {
    return Locale.forLanguageTag(tag).takeIf { it.language.isNotBlank() } ?: Locale.US
}
