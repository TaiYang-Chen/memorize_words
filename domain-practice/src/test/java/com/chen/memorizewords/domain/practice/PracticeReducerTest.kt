package com.chen.memorizewords.domain.practice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeReducerTest {

    private val reducer = PracticeReducer()

    @Test
    fun `start creates active question and requests speech`() {
        val result = reducer.reduce(
            state = emptySession(),
            action = PracticeAction.Start(
                sessionId = "s1",
                kind = PracticeKind.LISTENING_MEANING,
                words = sampleWords()
            )
        )

        assertEquals(PracticePhase.ANSWERING, result.state.phase)
        assertEquals("apple", result.state.activeQuestion?.word?.text)
        assertTrue(result.effects.first() is PracticeEffect.RequestSpeech)
    }

    @Test
    fun `wrong meaning answer moves to study and queues review`() {
        val started = startSession()
        val question = started.state.activeQuestion as MeaningChoiceQuestion
        val wrongChoice = question.choices.first { !it.isCorrect }

        val result = reducer.reduce(started.state, PracticeAction.SubmitChoice(question.id, wrongChoice.id))

        assertEquals(PracticePhase.STUDYING, result.state.phase)
        assertEquals(1, result.state.reviewQuestions.size)
        assertTrue(result.effects.any { it is PracticeEffect.ShowStudyCard })
    }

    @Test
    fun `continue revisits review question before report`() {
        val started = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "s1",
                kind = PracticeKind.LISTENING_SPELLING,
                words = listOf(sampleWords().first())
            )
        )
        val question = started.state.activeQuestion as SpellingQuestion
        val wrong = reducer.reduce(started.state, PracticeAction.SubmitText(question.id, "appl"))
        val review = reducer.reduce(wrong.state, PracticeAction.Continue)

        assertEquals(question.id, review.state.activeQuestion?.id)
        assertEquals(PracticePhase.ANSWERING, review.state.phase)
    }

    @Test
    fun `review question requires three correct answers before report`() {
        var current = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "s1",
                kind = PracticeKind.LISTENING_SPELLING,
                words = listOf(sampleWords().first())
            )
        )
        val question = current.state.activeQuestion as SpellingQuestion

        current = reducer.reduce(current.state, PracticeAction.SubmitText(question.id, "appl"))
        current = reducer.reduce(current.state, PracticeAction.Continue)

        repeat(2) {
            val reviewQuestion = current.state.activeQuestion as SpellingQuestion
            current = reducer.reduce(current.state, PracticeAction.SubmitText(reviewQuestion.id, "apple"))
            current = reducer.reduce(current.state, PracticeAction.Continue)
            assertEquals(PracticePhase.ANSWERING, current.state.phase)
            assertEquals(question.id, current.state.activeQuestion?.id)
        }

        val finalReviewQuestion = current.state.activeQuestion as SpellingQuestion
        current = reducer.reduce(current.state, PracticeAction.SubmitText(finalReviewQuestion.id, "apple"))
        current = reducer.reduce(current.state, PracticeAction.Continue)

        assertEquals(PracticePhase.REPORT, current.state.phase)
        assertEquals(3, current.state.report?.correctCount)
        assertEquals(1, current.state.report?.wrongCount)
    }

    @Test
    fun `report counts answered accuracy`() {
        val started = reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "s1",
                kind = PracticeKind.LISTENING_SPELLING,
                words = listOf(sampleWords().first())
            )
        )
        val question = started.state.activeQuestion as SpellingQuestion
        val answered = reducer.reduce(started.state, PracticeAction.SubmitText(question.id, "apple"))
        val report = reducer.reduce(answered.state, PracticeAction.Continue)

        assertEquals(PracticePhase.REPORT, report.state.phase)
        assertEquals(1, report.state.report?.correctCount)
        assertEquals(100, report.state.report?.accuracyPercent)
        assertTrue(report.effects.first() is PracticeEffect.CompleteSession)
    }

    private fun startSession(): PracticeReduceResult {
        return reducer.reduce(
            emptySession(),
            PracticeAction.Start(
                sessionId = "s1",
                kind = PracticeKind.LISTENING_MEANING,
                words = sampleWords()
            )
        )
    }

    private fun emptySession(): PracticeSession {
        return PracticeSession(sessionId = "", kind = PracticeKind.LISTENING_MEANING)
    }

    private fun sampleWords(): List<PracticeWord> {
        return listOf(
            PracticeWord(id = 1L, text = "apple", definitions = listOf("苹果")),
            PracticeWord(id = 2L, text = "book", definitions = listOf("书")),
            PracticeWord(id = 3L, text = "cat", definitions = listOf("猫"))
        )
    }
}
