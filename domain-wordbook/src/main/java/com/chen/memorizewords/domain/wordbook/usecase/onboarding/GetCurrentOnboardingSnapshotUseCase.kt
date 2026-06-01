package com.chen.memorizewords.domain.wordbook.usecase.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import javax.inject.Inject

class GetCurrentOnboardingSnapshotUseCase @Inject constructor(
    private val repository: OnboardingRepository
) {
    operator fun invoke(): OnboardingSnapshot = repository.getCurrentSnapshot()
}
