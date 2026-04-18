package com.chen.memorizewords.domain.usecase.onboarding

import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.service.onboarding.OnboardingStepResolver
import javax.inject.Inject

class GetCurrentOnboardingStepUseCase @Inject constructor(
    private val getCurrentOnboardingSnapshotUseCase: GetCurrentOnboardingSnapshotUseCase,
    private val onboardingStepResolver: OnboardingStepResolver
) {
    operator fun invoke(): OnboardingStep =
        onboardingStepResolver.resolve(getCurrentOnboardingSnapshotUseCase())
}
