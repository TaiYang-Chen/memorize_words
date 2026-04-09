package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.usecase.word.GetWordDefinitionsUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal class SpellingAssetLoader(
    private val scope: CoroutineScope,
    private val getWordDefinitions: GetWordDefinitionsUseCase,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val resourceProvider: ResourceProvider
) {

    private val meaningCache = mutableMapOf<Long, String>()
    private val speechCache = mutableMapOf<Long, SpeechAudioSuccess>()
    private val meaningInFlight = mutableMapOf<Long, Deferred<String>>()
    private val speechInFlight = mutableMapOf<Long, Deferred<SpeechAudioSuccess?>>()

    fun cachedMeaning(wordId: Long): String? = meaningCache[wordId]

    fun cachedSpeech(wordId: Long): SpeechAudioSuccess? = speechCache[wordId]

    fun loadMeaningAsync(word: Word): Deferred<String> {
        meaningCache[word.id]?.let { return CompletableDeferred(it) }
        return meaningInFlight.getOrPut(word.id) {
            scope.async {
                try {
                    val definition = getWordDefinitions(word.id).firstOrNull()
                    val meaning = buildMeaning(definition)
                    meaningCache[word.id] = meaning
                    meaning
                } finally {
                    meaningInFlight.remove(word.id)
                }
            }
        }
    }

    fun loadSpeechAsync(word: Word): Deferred<SpeechAudioSuccess?> {
        speechCache[word.id]?.let { return CompletableDeferred(it) }
        return speechInFlight.getOrPut(word.id) {
            scope.async {
                try {
                    val result = synthesizeSpeech(
                        SpeechTask.SynthesizeWord(text = word.word)
                    ) as? SpeechAudioSuccess
                    if (result != null) {
                        speechCache[word.id] = result
                    }
                    result
                } finally {
                    speechInFlight.remove(word.id)
                }
            }
        }
    }

    fun prefetch(word: Word?) {
        if (word == null) return
        runCatching { loadMeaningAsync(word) }
        runCatching { loadSpeechAsync(word) }
    }

    private fun buildMeaning(definition: WordDefinitions?): String {
        return if (definition != null) {
            resourceProvider.getString(
                R.string.practice_spelling_meaning_format,
                definition.partOfSpeech.abbr,
                definition.meaningChinese
            )
        } else {
            resourceProvider.getString(R.string.practice_spelling_meaning_fallback)
        }
    }
}
