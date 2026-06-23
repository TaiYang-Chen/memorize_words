package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.study.orchestrator.learning.LearningSessionTypes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningRecordTypeResolverTest {

    @Test
    fun `review session records mastered word as review`() {
        assertFalse(resolveLearningRecordIsNewWord(LearningSessionTypes.REVIEW))
    }

    @Test
    fun `new session records mastered word as new word`() {
        assertTrue(resolveLearningRecordIsNewWord(LearningSessionTypes.NEW))
    }

    @Test
    fun `unknown session type keeps existing new word default`() {
        assertTrue(resolveLearningRecordIsNewWord(Int.MAX_VALUE))
    }
}
