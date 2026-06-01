package com.chen.memorizewords.domain.wordbook.service.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import javax.inject.Inject

class OnboardingStepResolver @Inject constructor() {
    fun resolve(snapshot: OnboardingSnapshot): OnboardingStep {
        return when (snapshot.phase) {
            OnboardingPhase.NEEDS_WORD_BOOK,
            OnboardingPhase.NEEDS_STUDY_PLAN -> OnboardingStep.SELECT_WORD_BOOK
            OnboardingPhase.COMPLETED -> OnboardingStep.COMPLETED
        }
    }
}
