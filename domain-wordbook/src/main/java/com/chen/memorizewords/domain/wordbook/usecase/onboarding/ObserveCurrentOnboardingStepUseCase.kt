package com.chen.memorizewords.domain.wordbook.usecase.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.wordbook.service.onboarding.OnboardingStepResolver
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveCurrentOnboardingStepUseCase @Inject constructor(
    private val observeCurrentOnboardingSnapshotUseCase: ObserveCurrentOnboardingSnapshotUseCase,
    private val onboardingStepResolver: OnboardingStepResolver
) {
    operator fun invoke(): Flow<OnboardingStep> =
        observeCurrentOnboardingSnapshotUseCase()
            .map(onboardingStepResolver::resolve)
            .distinctUntilChanged()
}
