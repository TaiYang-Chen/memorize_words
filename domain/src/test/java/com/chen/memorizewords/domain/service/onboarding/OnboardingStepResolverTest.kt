package com.chen.memorizewords.domain.service.onboarding

import com.chen.memorizewords.domain.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepResolverTest {

    private val resolver = OnboardingStepResolver()

    @Test
    fun `resolve returns select word book for NEEDS_WORD_BOOK`() {
        val step = resolver.resolve(
            OnboardingSnapshot(phase = OnboardingPhase.NEEDS_WORD_BOOK)
        )

        assertEquals(OnboardingStep.SELECT_WORD_BOOK, step)
    }

    @Test
    fun `resolve returns select word book for NEEDS_STUDY_PLAN`() {
        val step = resolver.resolve(
            OnboardingSnapshot(
                phase = OnboardingPhase.NEEDS_STUDY_PLAN,
                selectedWordBookId = 12L
            )
        )

        assertEquals(OnboardingStep.SELECT_WORD_BOOK, step)
    }

    @Test
    fun `resolve falls back to select word book when study plan snapshot is invalid`() {
        val step = resolver.resolve(
            OnboardingSnapshot(
                phase = OnboardingPhase.NEEDS_STUDY_PLAN,
                selectedWordBookId = null
            )
        )

        assertEquals(OnboardingStep.SELECT_WORD_BOOK, step)
    }

    @Test
    fun `resolve returns completed for COMPLETED`() {
        val step = resolver.resolve(
            OnboardingSnapshot(
                phase = OnboardingPhase.COMPLETED,
                selectedWordBookId = 12L,
                completedAt = 999L
            )
        )

        assertEquals(OnboardingStep.COMPLETED, step)
    }
}
