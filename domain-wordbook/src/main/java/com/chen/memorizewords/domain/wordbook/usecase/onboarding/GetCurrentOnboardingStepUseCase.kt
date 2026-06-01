package com.chen.memorizewords.domain.wordbook.usecase.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.wordbook.service.onboarding.OnboardingStepResolver
import javax.inject.Inject

class GetCurrentOnboardingStepUseCase @Inject constructor(
    private val getCurrentOnboardingSnapshotUseCase: GetCurrentOnboardingSnapshotUseCase,
    private val onboardingStepResolver: OnboardingStepResolver
) {
    operator fun invoke(): OnboardingStep =
        onboardingStepResolver.resolve(getCurrentOnboardingSnapshotUseCase())
}
