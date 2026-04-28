package com.chen.memorizewords.feature.learning.ui.practice.listening.presentation

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.words.word.PronunciationType
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeMode
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_SPEECH_LOCALE_UK
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_SPEECH_LOCALE_US

internal data class ListeningStudyPronunciationUi(
    val pronunciationType: PronunciationType,
    val localeLabel: String,
    val phoneticText: String,
    val speechLocale: String,
    val toggleEnabled: Boolean
)

internal class ListeningUiMapper(
    private val resourceProvider: ResourceProvider
) {
    fun displayName(mode: ListeningPracticeMode): String {
        return when (mode) {
            ListeningPracticeMode.SPELLING -> "\u8fa8\u97f3\u62fc\u5199"
            ListeningPracticeMode.MEANING -> "\u8fa8\u97f3\u9009\u4e49"
        }
    }

    fun resolveMode(modeName: String?): ListeningPracticeMode {
        return runCatching {
            ListeningPracticeMode.valueOf(modeName.orEmpty())
        }.getOrDefault(ListeningPracticeMode.MEANING)
    }

    fun screenTitle(progressText: String): String {
        return "\u542c\u529b\u6d4b\u8bd5\uff08$progressText\uff09"
    }

    fun progressText(completed: Int, total: Int): String {
        return resourceProvider.getString(
            R.string.practice_listening_progress_format,
            completed,
            total
        )
    }

    fun reviewProgressText(reviewCount: Int, total: Int): String {
        return resourceProvider.getString(
            R.string.practice_listening_review_progress_format,
            reviewCount,
            total
        )
    }

    fun modeBadge(mode: ListeningPracticeMode): String {
        return resourceProvider.getString(
            R.string.practice_listening_mode_badge,
            displayName(mode)
        )
    }

    fun phoneticChip(word: Word): String {
        return resourceProvider.getString(
            R.string.practice_listening_phonetic_chip,
            normalizePhonetic(
                word.phoneticUS?.takeIf { it.isNotBlank() }
                    ?: word.phoneticUK?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun studyPronunciation(
        word: Word,
        preferredType: PronunciationType? = null
    ): ListeningStudyPronunciationUi {
        val hasUs = hasPhonetic(word, PronunciationType.US)
        val hasUk = hasPhonetic(word, PronunciationType.UK)
        val resolvedType = when {
            preferredType == PronunciationType.US && hasUs -> PronunciationType.US
            preferredType == PronunciationType.UK && hasUk -> PronunciationType.UK
            hasUs -> PronunciationType.US
            hasUk -> PronunciationType.UK
            else -> PronunciationType.US
        }
        return ListeningStudyPronunciationUi(
            pronunciationType = resolvedType,
            localeLabel = when {
                !hasUs && !hasUk -> ""
                resolvedType == PronunciationType.US -> "\u7f8e"
                else -> "\u82f1"
            },
            phoneticText = normalizePhonetic(resolvePhoneticValue(word, resolvedType)),
            speechLocale = resolveSpeechLocale(resolvedType),
            toggleEnabled = hasUs && hasUk
        )
    }

    fun resolveSpeechLocale(pronunciationType: PronunciationType): String {
        return when (pronunciationType) {
            PronunciationType.US -> LISTENING_SPEECH_LOCALE_US
            PronunciationType.UK -> LISTENING_SPEECH_LOCALE_UK
        }
    }

    fun resolveReportSpeechLocale(word: Word): String {
        return when {
            !word.phoneticUS.isNullOrBlank() -> LISTENING_SPEECH_LOCALE_US
            !word.phoneticUK.isNullOrBlank() -> LISTENING_SPEECH_LOCALE_UK
            else -> LISTENING_SPEECH_LOCALE_US
        }
    }

    fun normalizePhonetic(phonetic: String?): String {
        val fallback = resourceProvider.getString(R.string.practice_listening_phonetic_empty)
        val value = phonetic?.takeIf { it.isNotBlank() } ?: return fallback
        return value.trim().trim('[', ']', '/', ' ')
            .takeIf { it.isNotBlank() }
            ?.let { "/$it/" }
            ?: fallback
    }

    private fun hasPhonetic(word: Word, pronunciationType: PronunciationType): Boolean {
        return !resolvePhoneticValue(word, pronunciationType).isNullOrBlank()
    }

    private fun resolvePhoneticValue(
        word: Word,
        pronunciationType: PronunciationType
    ): String? {
        return when (pronunciationType) {
            PronunciationType.US -> word.phoneticUS
            PronunciationType.UK -> word.phoneticUK
        }?.takeIf { it.isNotBlank() }
    }
}
