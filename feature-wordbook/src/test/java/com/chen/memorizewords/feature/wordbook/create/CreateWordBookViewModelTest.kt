package com.chen.memorizewords.feature.wordbook.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateWordBookViewModelTest {

    @Test
    fun parseWordsIgnoresBlankLinesAndCountsDuplicates() {
        val stats = parseWords(
            """
            abandon

            derive
            Abandon
            compile
            """.trimIndent()
        )

        assertEquals(listOf("abandon", "derive", "compile"), stats.words)
        assertEquals(1, stats.blankLineCount)
        assertEquals(1, stats.duplicateLineCount)
    }

    @Test
    fun parseWordsMarksTooLongWord() {
        val stats = parseWords("a".repeat(CreateWordBookViewModel.WORD_MAX_LENGTH + 1))

        assertEquals(1, stats.validWordCount)
        assertTrue(stats.hasTooLongWord)
    }
}
