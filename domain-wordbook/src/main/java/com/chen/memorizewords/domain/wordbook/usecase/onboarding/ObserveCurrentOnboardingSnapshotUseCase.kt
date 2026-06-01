package com.chen.memorizewords.domain.wordbook.usecase.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentOnboardingSnapshotUseCase @Inject constructor(
    private val repository: OnboardingRepository
) {
    operator fun invoke(): Flow<OnboardingSnapshot> = repository.observeCurrentSnapshot()
}
