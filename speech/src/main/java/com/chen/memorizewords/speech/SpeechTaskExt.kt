package com.chen.memorizewords.speech

import com.chen.memorizewords.speech.api.SpeechTask
import java.util.Locale

internal fun SpeechTask.cacheDescriptor(providerName: String): String {
    return when (this) {
        is SpeechTask.SynthesizeSentence -> listOf(
            providerName,
            requiredCapability.name,
            voice,
            locale,
            text,
            audioFormat.mimeType,
            audioFormat.sampleRateHz.toString(),
            audioFormat.channelCount.toString(),
            audioFormat.encoding
        ).joinToString(separator = "|")

        is SpeechTask.SynthesizeWord -> listOf(
            providerName,
            requiredCapability.name,
            voice,
            locale,
            text,
            audioFormat.mimeType,
            audioFormat.sampleRateHz.toString(),
            audioFormat.channelCount.toString(),
            audioFormat.encoding
        ).joinToString(separator = "|")

        is SpeechTask.EvaluateShadowing -> listOf(
            providerName,
            requiredCapability.name,
            locale,
            referenceText
        ).joinToString(separator = "|")
    }
}

internal fun localeFromTag(tag: String): Locale {
    return Locale.forLanguageTag(tag).takeIf { it.language.isNotBlank() } ?: Locale.US
}
