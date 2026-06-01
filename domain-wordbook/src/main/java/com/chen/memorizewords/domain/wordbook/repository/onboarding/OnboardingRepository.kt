package com.chen.memorizewords.domain.wordbook.repository.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    fun getCurrentSnapshot(): OnboardingSnapshot
    fun observeCurrentSnapshot(): Flow<OnboardingSnapshot>
    suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?)
    suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?)
    suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot
}
