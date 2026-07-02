package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.word.model.word.Word
import kotlin.test.Test
import kotlin.test.assertEquals

class LearningWordOrderingTest {

    @Test
    fun `orders loaded words by requested ids`() {
        val loaded = listOf(
            testWord(3L, "calm"),
            testWord(1L, "able"),
            testWord(2L, "brave")
        )

        val ordered = orderWordsByIds(listOf(2L, 3L, 1L), loaded)

        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }
}

private fun testWord(id: Long, value: String): Word {
    return Word(
        id = id,
        word = value,
        normalizedWord = value,
        phoneticUS = null,
        phoneticUK = null,
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
