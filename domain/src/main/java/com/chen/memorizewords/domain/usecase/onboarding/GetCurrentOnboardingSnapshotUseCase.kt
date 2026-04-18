package com.chen.memorizewords.domain.usecase.onboarding

import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.repository.onboarding.OnboardingRepository
import javax.inject.Inject

class GetCurrentOnboardingSnapshotUseCase @Inject constructor(
    private val repository: OnboardingRepository
) {
    operator fun invoke(): OnboardingSnapshot = repository.getCurrentSnapshot()
}
