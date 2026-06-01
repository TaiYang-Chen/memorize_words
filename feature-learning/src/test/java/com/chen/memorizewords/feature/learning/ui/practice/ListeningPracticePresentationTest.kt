package com.chen.memorizewords.feature.learning.ui.practice

import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.feature.learning.shouldUseListeningCustomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningPracticePresentationTest {

    @Test
    fun `buildListeningScreenTitle uses listening test format`() {
        assertEquals("听力测试（3/10）", buildListeningScreenTitle("3/10"))
    }

    @Test
    fun `listeningModeDisplayName matches screenshot wording`() {
        assertEquals("辨音拼写", listeningModeDisplayName(ListeningPracticeMode.SPELLING))
        assertEquals("辨音选义", listeningModeDisplayName(ListeningPracticeMode.MEANING))
    }

    @Test
    fun `resolveListeningPracticeMode falls back to meaning mode`() {
        assertEquals(ListeningPracticeMode.MEANING, resolveListeningPracticeMode(null))
        assertEquals(ListeningPracticeMode.MEANING, resolveListeningPracticeMode("INVALID"))
        assertEquals(ListeningPracticeMode.MEANING, resolveListeningPracticeMode("RANDOM"))
        assertEquals(ListeningPracticeMode.SPELLING, resolveListeningPracticeMode("SPELLING"))
    }

    @Test
    fun `shouldUseListeningCustomHeader only applies to listening mode`() {
        assertTrue(shouldUseListeningCustomHeader(PracticeMode.LISTENING))
        assertFalse(shouldUseListeningCustomHeader(PracticeMode.EXAM))
        assertFalse(shouldUseListeningCustomHeader(PracticeMode.SHADOWING))
    }
}
