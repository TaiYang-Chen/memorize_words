package com.chen.memorizewords.feature.learning.ui.practice.listening.presentation

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeMode
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_SPEECH_LOCALE_UK
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_SPEECH_LOCALE_US
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningUiMapperTest {

    private val mapper = ListeningUiMapper(FakeResourceProvider())

    @Test
    fun `maps mode names progress text and badges`() {
        assertEquals("辨音选义", mapper.displayName(ListeningPracticeMode.MEANING))
        assertEquals("辨音拼写", mapper.displayName(ListeningPracticeMode.SPELLING))
        assertEquals(ListeningPracticeMode.MEANING, mapper.resolveMode("unknown"))
        assertEquals(ListeningPracticeMode.SPELLING, mapper.resolveMode("SPELLING"))
        assertEquals("听力测试（3/9）", mapper.screenTitle("3/9"))
        assertEquals("2/8", mapper.progressText(2, 8))
        assertEquals("复习 1/8", mapper.reviewProgressText(1, 8))
        assertEquals("模式 辨音选义", mapper.modeBadge(ListeningPracticeMode.MEANING))
    }

    @Test
    fun `normalizes phonetic text and falls back when missing`() {
        assertEquals("/al/", mapper.normalizePhonetic("[al]"))
        assertEquals("/be/", mapper.normalizePhonetic("/be/"))
        assertEquals("/--/", mapper.normalizePhonetic(" "))
        assertEquals(
            "/uk/",
            mapper.phoneticChip(word(phoneticUS = null, phoneticUK = "uk"))
        )
    }

    @Test
    fun `study pronunciation prefers available locale and enables toggle only when both exist`() {
        val both = mapper.studyPronunciation(
            word = word(phoneticUS = "us", phoneticUK = "uk"),
            preferredType = PronunciationType.UK
        )

        assertEquals(PronunciationType.UK, both.pronunciationType)
        assertEquals("英", both.localeLabel)
        assertEquals("/uk/", both.phoneticText)
        assertEquals(LISTENING_SPEECH_LOCALE_UK, both.speechLocale)
        assertTrue(both.toggleEnabled)

        val onlyUs = mapper.studyPronunciation(word(phoneticUS = "us", phoneticUK = null))
        assertEquals(PronunciationType.US, onlyUs.pronunciationType)
        assertEquals(LISTENING_SPEECH_LOCALE_US, onlyUs.speechLocale)
        assertFalse(onlyUs.toggleEnabled)
    }

    @Test
    fun `report speech locale follows available phonetic data`() {
        assertEquals(
            LISTENING_SPEECH_LOCALE_US,
            mapper.resolveReportSpeechLocale(word(phoneticUS = "us", phoneticUK = "uk"))
        )
        assertEquals(
            LISTENING_SPEECH_LOCALE_UK,
            mapper.resolveReportSpeechLocale(word(phoneticUS = null, phoneticUK = "uk"))
        )
        assertEquals(
            LISTENING_SPEECH_LOCALE_US,
            mapper.resolveReportSpeechLocale(word(phoneticUS = null, phoneticUK = null))
        )
    }

    private fun word(
        phoneticUS: String?,
        phoneticUK: String?
    ): Word {
        return Word(
            id = 1L,
            word = "alpha",
            normalizedWord = "alpha",
            phoneticUS = phoneticUS,
            phoneticUK = phoneticUK,
            hasIrregularForms = false,
            memoryTip = null,
            mnemonicImageUrl = null,
            memoryAssociations = emptyList(),
            wordFamily = null,
            synonyms = emptyList(),
            antonyms = emptyList(),
            tags = emptyList(),
            notes = null,
            rootMemoryTip = null
        )
    }

    private class FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                R.string.practice_listening_progress_format ->
                    "${formatArgs[0]}/${formatArgs[1]}"
                R.string.practice_listening_review_progress_format ->
                    "复习 ${formatArgs[0]}/${formatArgs[1]}"
                R.string.practice_listening_mode_badge ->
                    "模式 ${formatArgs[0]}"
                R.string.practice_listening_phonetic_chip ->
                    formatArgs[0].toString()
                R.string.practice_listening_phonetic_empty ->
                    "/--/"
                else -> error("Unexpected string resource: $resId")
            }
        }
    }
}
