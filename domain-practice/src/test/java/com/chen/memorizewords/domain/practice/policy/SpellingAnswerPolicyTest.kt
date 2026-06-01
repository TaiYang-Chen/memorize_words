package com.chen.memorizewords.domain.practice.policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellingAnswerPolicyTest {

    private val policy = SpellingAnswerPolicy()

    @Test
    fun `sanitize uppercases trims and clamps to answer length`() {
        assertEquals("APPEN", policy.sanitizeAnswerInput("APPLE", " appending "))
    }

    @Test
    fun `reconcile preserves locked hint prefix`() {
        assertEquals("APX", policy.reconcileKeyboardInput("APPLE", 2, "zzx"))
    }

    @Test
    fun `hint reveals first mismatched character`() {
        val hint = policy.applyHint("APPLE", "AX", 1)

        assertEquals("AP", hint?.answer)
        assertEquals(2, hint?.hintLockedLength)
    }

    @Test
    fun `correct ignores case and surrounding whitespace`() {
        assertTrue(policy.isCorrect(" apple ", "APPLE"))
        assertFalse(policy.isCorrect("apply", "APPLE"))
    }

    @Test
    fun `correct ignoring whitespace supports phrase answers`() {
        assertTrue(policy.isCorrectIgnoringWhitespace("icecream", "ice cream"))
        assertFalse(policy.isCorrectIgnoringWhitespace("icebread", "ice cream"))
    }
}
