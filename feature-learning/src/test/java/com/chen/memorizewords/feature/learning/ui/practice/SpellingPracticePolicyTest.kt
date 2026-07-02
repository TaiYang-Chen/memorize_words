package com.chen.memorizewords.feature.learning.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.practice.policy.SpellingAnswerPolicy
import com.chen.memorizewords.feature.learning.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpellingPracticePolicyTest {

    private val answerPolicy = SpellingAnswerPolicy()
    private val helper = SpellingUiHelper(FakeResourceProvider(), answerPolicy)

    @Test
    fun `keyboard input preserves case truncates and preserves locked hint prefix`() {
        assertEquals(
            "Meab",
            helper.reconcileKeyboardInput(
                answerWord = "Memory",
                hintLockedLength = 2,
                input = "zzab"
            )
        )
    }

    @Test
    fun `hint reveals first wrong or next missing character and locks prefix`() {
        val hint = answerPolicy.applyHint(
            answerWord = "filter",
            currentAnswer = "fal",
            hintLockedLength = 1
        )

        assertEquals("fil", hint?.answer)
        assertEquals(2, hint?.hintLockedLength)
    }

    @Test
    fun `letter pool consumes duplicate letters independently`() {
        val letters = helper.buildLetterItems(
            poolChars = listOf('p', 'p', 'a', 'l', 'e'),
            answer = "P"
        )

        assertFalse(letters[0].enabled)
        assertTrue(letters[1].enabled)
        assertTrue(letters[2].enabled)
    }

    @Test
    fun `wrong slot indexes mark missing and mismatched positions once`() {
        assertEquals(
            listOf(2, 3, 4),
            helper.findWrongSlotIndexes(answerWord = "smart", input = "smX")
        )
        assertEquals("还有 3 处需要调整，请再试一次", helper.buildRetryFeedback("SMART", "SMX"))
    }

    @Test
    fun `session record is saved only after meaningful spelling activity`() {
        assertFalse(
            shouldSavePracticeSession(
                totalDurationMs = 0L,
                summary = PracticeSessionSummary(questionCount = 3),
                wordIds = listOf(1L)
            )
        )
        assertFalse(
            shouldSavePracticeSession(
                totalDurationMs = 1000L,
                summary = PracticeSessionSummary(questionCount = 0),
                wordIds = listOf(1L)
            )
        )
        assertTrue(
            shouldSavePracticeSession(
                totalDurationMs = 1000L,
                summary = PracticeSessionSummary(questionCount = 3),
                wordIds = listOf(1L)
            )
        )
    }
}

private class FakeResourceProvider : ResourceProvider {
    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return when (resId) {
            R.string.practice_spelling_retry_feedback ->
                "还有 ${formatArgs[0]} 处需要调整，请再试一次"

            R.string.practice_spelling_summary ->
                "完成 ${formatArgs[0]}/${formatArgs[1]} · 正确 ${formatArgs[2]} · 正确率 ${formatArgs[3]}% · 提交 ${formatArgs[4]} 次"

            else -> error("Unexpected string resource $resId")
        }
    }
}
