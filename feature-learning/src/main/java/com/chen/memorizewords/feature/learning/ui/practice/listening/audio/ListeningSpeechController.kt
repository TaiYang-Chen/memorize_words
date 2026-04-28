package com.chen.memorizewords.feature.learning.ui.practice.listening.audio

import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask

internal data class ListeningWordSpeechKey(
    val wordId: Long,
    val locale: String
)

internal data class ListeningSentenceSpeechKey(
    val wordId: Long,
    val text: String,
    val locale: String
)

internal class ListeningSpeechController(
    private val synthesizeSpeech: SynthesizeSpeechUseCase
) {
    private val wordSpeechCache = mutableMapOf<ListeningWordSpeechKey, SpeechAudioSuccess?>()
    private val sentenceSpeechCache = mutableMapOf<ListeningSentenceSpeechKey, SpeechAudioSuccess?>()

    fun clear() {
        wordSpeechCache.clear()
        sentenceSpeechCache.clear()
    }

    fun cachedWordSpeech(key: ListeningWordSpeechKey): SpeechAudioSuccess? {
        return wordSpeechCache[key]
    }

    suspend fun resolveWordSpeech(
        key: ListeningWordSpeechKey,
        text: String
    ): SpeechAudioSuccess? {
        if (wordSpeechCache.containsKey(key)) return wordSpeechCache[key]
        val speech = synthesizeSpeech(
            SpeechTask.SynthesizeWord(
                text = text,
                locale = key.locale
            )
        ) as? SpeechAudioSuccess
        wordSpeechCache[key] = speech
        return speech
    }

    suspend fun resolveSentenceSpeech(
        key: ListeningSentenceSpeechKey
    ): SpeechAudioSuccess? {
        if (sentenceSpeechCache.containsKey(key)) return sentenceSpeechCache[key]
        val speech = synthesizeSpeech(
            SpeechTask.SynthesizeSentence(
                text = key.text,
                locale = key.locale
            )
        ) as? SpeechAudioSuccess
        sentenceSpeechCache[key] = speech
        return speech
    }
}
