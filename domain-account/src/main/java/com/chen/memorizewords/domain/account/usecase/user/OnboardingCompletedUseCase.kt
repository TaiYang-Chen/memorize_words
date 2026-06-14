package com.chen.memorizewords.domain.account.usecase.user
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import javax.inject.Inject

class OnboardingCompletedUseCase @Inject constructor(
    private val repo: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repo.onboardingCompleted()
    }
}
