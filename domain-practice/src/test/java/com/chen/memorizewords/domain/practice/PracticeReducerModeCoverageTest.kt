package com.chen.memorizewords.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeReducerModeCoverageTest {

    private val reducer = PracticeReducer()

    @Test
    fun `shadowing submission can complete with score`() {
        val started = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "shadowing-session",
                kind = PracticeKind.SHADOWING,
                words = sampleWords().take(1)
            )
        )

        val question = started.state.activeQuestion as ShadowingQuestion
        val answered = reducer.reduce(
            started.state,
            PracticeAction.SubmitShadowing(
                questionId = question.id,
                passed = true,
                score = 91f
            )
        )
        val completed = reducer.reduce(answered.state, PracticeAction.Continue)

        assertEquals(PracticePhase.REPORT, completed.state.phase)
        assertEquals(1, completed.state.report?.correctCount)
        assertEquals(100, completed.state.report?.accuracyPercent)
        assertTrue(completed.effects.first() is PracticeEffect.CompleteSession)
    }

    @Test
    fun `audio loop answer advances without review when playback completed`() {
        val started = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "audio-loop-session",
                kind = PracticeKind.AUDIO_LOOP,
                words = sampleWords().take(1)
            )
        )

        val question = started.state.activeQuestion as AudioLoopQuestion
        val answered = reducer.reduce(
            started.state,
            PracticeAction.SubmitText(question.id, "played")
        )
        val completed = reducer.reduce(answered.state, PracticeAction.Continue)

        assertEquals(PracticePhase.FEEDBACK, answered.state.phase)
        assertTrue(answered.effects.any { it is PracticeEffect.AdvanceAfterDelay })
        assertEquals(PracticePhase.REPORT, completed.state.phase)
        assertEquals(1, completed.state.report?.correctCount)
    }

    @Test
    fun `exam start uses exam question kind and meaning choices`() {
        val started = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "exam-session",
                kind = PracticeKind.EXAM,
                words = sampleWords()
            )
        )

        val question = started.state.activeQuestion as MeaningChoiceQuestion

        assertEquals(PracticeKind.EXAM, question.kind)
        assertTrue(question.choices.isNotEmpty())
        assertTrue(question.choices.any { it.isCorrect })
    }

    private fun emptySession(): PracticeSession {
        return PracticeSession(sessionId = "", kind = PracticeKind.LISTENING_MEANING)
    }

    private fun sampleWords(): List<PracticeWord> {
        return listOf(
            PracticeWord(id = 1L, text = "apple", definitions = listOf("fruit")),
            PracticeWord(id = 2L, text = "book", definitions = listOf("reading")),
            PracticeWord(id = 3L, text = "cat", definitions = listOf("animal"))
        )
    }
}
