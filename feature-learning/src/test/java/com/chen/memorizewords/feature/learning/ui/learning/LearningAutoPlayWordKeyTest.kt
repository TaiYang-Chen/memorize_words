package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.word.model.word.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LearningAutoPlayWordKeyTest {

    @Test
    fun `same page word and token produce same auto play key`() {
        val first = resolveLearningAutoPlayWordKey(
            testLearningState(
                learningState = LearningViewModel.LearningState.TEST,
                wordId = 1L,
                questionToken = 7
            )
        )
        val second = resolveLearningAutoPlayWordKey(
            testLearningState(
                learningState = LearningViewModel.LearningState.TEST,
                wordId = 1L,
                questionToken = 7
            )
        )

        assertEquals(first, second)
    }

    @Test
    fun `test and detail pages produce different auto play keys for same word and token`() {
        val testKey = resolveLearningAutoPlayWordKey(
            testLearningState(
                learningState = LearningViewModel.LearningState.TEST,
                wordId = 1L,
                questionToken = 7
            )
        )
        val detailKey = resolveLearningAutoPlayWordKey(
            testLearningState(
                learningState = LearningViewModel.LearningState.DETAIL,
                wordId = 1L,
                questionToken = 7
            )
        )

        assertNotEquals(testKey, detailKey)
    }

    @Test
    fun `hidden word surface does not produce auto play key`() {
        val key = resolveLearningAutoPlayWordKey(
            testLearningState(showWordSurface = false)
        )

        assertNull(key)
    }

    private fun testLearningState(
        learningState: LearningViewModel.LearningState = LearningViewModel.LearningState.TEST,
        wordId: Long = 1L,
        questionToken: Int = 1,
        showWordSurface: Boolean = true
    ): LearningViewModel.LearningUiState {
        return LearningViewModel.LearningUiState(
            learningState = learningState,
            currentWord = testWord(wordId, "able"),
            showWordSurface = showWordSurface,
            questionToken = questionToken
        )
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
}
