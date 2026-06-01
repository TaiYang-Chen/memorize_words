package com.chen.memorizewords.domain.wordbook.usecase.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import javax.inject.Inject

class InitializeOnboardingSnapshotUseCase @Inject constructor(
    private val repository: OnboardingRepository
) {
    suspend operator fun invoke(userId: Long, snapshot: OnboardingSnapshot?) {
        repository.initializeSnapshotForUser(userId, snapshot)
    }
}
