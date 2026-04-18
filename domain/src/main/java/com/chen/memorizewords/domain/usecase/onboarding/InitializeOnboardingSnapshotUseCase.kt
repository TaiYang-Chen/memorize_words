package com.chen.memorizewords.domain.usecase.onboarding

import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.repository.onboarding.OnboardingRepository
import javax.inject.Inject

class InitializeOnboardingSnapshotUseCase @Inject constructor(
    private val repository: OnboardingRepository
) {
    suspend operator fun invoke(userId: Long, snapshot: OnboardingSnapshot?) {
        repository.initializeSnapshotForUser(userId, snapshot)
    }
}
