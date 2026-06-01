package com.chen.memorizewords.data.sync.local.mmkv.onboarding

import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import kotlinx.coroutines.flow.Flow

interface OnboardingSnapshotDataSource {
    fun getSnapshot(userId: Long): OnboardingSnapshot?

    fun observeSnapshot(userId: Long): Flow<OnboardingSnapshot?>

    suspend fun saveSnapshot(userId: Long, snapshot: OnboardingSnapshot)

    suspend fun clearSnapshot(userId: Long)
}
