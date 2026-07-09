package com.chen.memorizewords.domain.study.model.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningSessionEngineTest {

    @Test
    fun `mastering wrong word on first review completes session without showing it again`() {
        val engine = LearningSessionEngine(wordIds = listOf(1L))

        engine.submitAnswer(isCorrect = false)
        assertEquals(1L, engine.moveToNext().currentWordId)

        val result = engine.markCurrentWordMastered()
        assertEquals(1L, result.completedWordId)

        val next = engine.moveToNext()
        assertNull(next.currentWordId)
        assertTrue(next.isFinished)
    }

    @Test
    fun `mastering wrong word on second review completes session without showing it again`() {
        val engine = LearningSessionEngine(wordIds = listOf(1L))

        engine.submitAnswer(isCorrect = false)
        engine.moveToNext()
        val firstReview = engine.submitAnswer(isCorrect = true)
        assertNull(firstReview.completedWordId)
        assertFalse(firstReview.snapshot.isCurrentWordCompleted)

        assertEquals(1L, engine.moveToNext().currentWordId)
        val secondReview = engine.markCurrentWordMastered()
        assertEquals(1L, secondReview.completedWordId)

        val next = engine.moveToNext()
        assertNull(next.currentWordId)
        assertTrue(next.isFinished)
    }

    @Test
    fun `mastered wrong word leaves rotation while unfinished words continue`() {
        val engine = LearningSessionEngine(wordIds = listOf(1L, 2L, 3L))

        engine.submitAnswer(isCorrect = false)
        assertEquals(1L, engine.moveToNext().currentWordId)
        assertEquals(1L, engine.markCurrentWordMastered().completedWordId)

        val next = engine.moveToNext()
        assertEquals(2L, next.currentWordId)
        assertFalse(next.isFinished)
    }

    @Test
    fun `wrong word remains in review until consecutive correct target is reached`() {
        val engine = LearningSessionEngine(
            wordIds = listOf(1L),
            wrongTrackMasteryTarget = 4
        )

        engine.submitAnswer(isCorrect = false)
        repeat(3) {
            engine.moveToNext()
            val result = engine.submitAnswer(isCorrect = true)
            assertNull(result.completedWordId)
            assertFalse(result.snapshot.isCurrentWordCompleted)
        }

        engine.moveToNext()
        val completed = engine.submitAnswer(isCorrect = true)
        assertEquals(1L, completed.completedWordId)
        assertTrue(completed.snapshot.isCurrentWordCompleted)
    }

    @Test
    fun `mastered word is not shown again even when input contains duplicate candidates`() {
        val engine = LearningSessionEngine(wordIds = listOf(1L, 1L))
        assertEquals(1, engine.snapshot().totalWordsCount)

        assertEquals(1L, engine.submitAnswer(isCorrect = true).completedWordId)
        val next = engine.moveToNext()

        assertNull(next.currentWordId)
        assertTrue(next.isFinished)
    }
}
