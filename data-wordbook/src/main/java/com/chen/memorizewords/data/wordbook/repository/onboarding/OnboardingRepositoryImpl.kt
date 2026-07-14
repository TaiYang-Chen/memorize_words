package com.chen.memorizewords.data.wordbook.repository.onboarding

import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    private val localAccountStore: LocalAccountStore,
    private val localAccountRepository: LocalAccountRepository,
    private val onboardingSnapshotDataSource: OnboardingSnapshotDataSource,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val directSyncLauncher: DirectSyncLauncher
) : OnboardingRepository {

    override fun getCurrentSnapshot(): OnboardingSnapshot {
        val userId = localAccountStore.getUserId() ?: return DEFAULT_SNAPSHOT
        return normalizeSnapshot(
            snapshot = onboardingSnapshotDataSource.getSnapshot(userId) ?: DEFAULT_SNAPSHOT,
            existing = null
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> {
        return localAccountStore.getUserFlow()
            .flatMapLatest { user ->
                val userId = user?.userId ?: return@flatMapLatest flowOf(DEFAULT_SNAPSHOT)
                onboardingSnapshotDataSource.observeSnapshot(userId)
                    .map { snapshot ->
                        normalizeSnapshot(snapshot = snapshot ?: DEFAULT_SNAPSHOT, existing = null)
                    }
            }
            .distinctUntilChanged()
    }

    override suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?) {
        withContext(Dispatchers.IO) {
            val existing = onboardingSnapshotDataSource.getSnapshot(userId)
            val resolved = resolvePreferredSnapshot(
                existing = existing,
                incoming = snapshot
            ) ?: DEFAULT_SNAPSHOT
            onboardingSnapshotDataSource.saveSnapshot(userId, resolved)
        }
    }

    override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) {
        if (snapshot == null) return
        withContext(Dispatchers.IO) {
            val userId = localAccountStore.getUserId() ?: return@withContext
            val current = onboardingSnapshotDataSource.getSnapshot(userId)
            val resolved = resolvePreferredSnapshot(
                existing = current,
                incoming = snapshot
            ) ?: normalizeSnapshot(snapshot, current)
            onboardingSnapshotDataSource.saveSnapshot(userId, resolved)
        }
    }

    override suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot {
        return withContext(Dispatchers.IO) {
            val userId = requireCurrentUserId()
            val current = normalizeSnapshot(
                snapshot = onboardingSnapshotDataSource.getSnapshot(userId) ?: DEFAULT_SNAPSHOT,
                existing = null
            )
            if (current.phase == OnboardingPhase.COMPLETED) {
                return@withContext current
            }
            val now = System.currentTimeMillis()
            val next = current.copy(
                phase = OnboardingPhase.COMPLETED,
                selectedWordBookId = selectedWordBookId,
                revision = current.revision + 1L,
                updatedAtMs = now,
                completedAt = current.completedAt ?: now
            )
            onboardingSnapshotDataSource.saveSnapshot(userId, next)
            markCurrentUserOnboardingCompleted(userId)
            uploadSnapshot(next)
            next
        }
    }

    private suspend fun markCurrentUserOnboardingCompleted(userId: Long) {
        val currentUser = localAccountRepository.getCurrentUser() ?: return
        if (currentUser.userId != userId || currentUser.onboardingCompleted) return
        localAccountRepository.saveUser(currentUser.copy(onboardingCompleted = true))
    }

    private fun uploadSnapshot(snapshot: OnboardingSnapshot) {
        directSyncLauncher.launch(
            operation = "onboarding_state",
            orderingKey = "onboarding",
            request = { remoteUserSyncDataSource.updateOnboardingState(snapshot) }
        )
    }

    private fun requireCurrentUserId(): Long {
        return localAccountStore.getUserId()
            ?: throw IllegalStateException("current user is required")
    }
}

private val DEFAULT_SNAPSHOT = OnboardingSnapshot()

private fun resolvePreferredSnapshot(
    existing: OnboardingSnapshot?,
    incoming: OnboardingSnapshot?
): OnboardingSnapshot? {
    if (existing == null) {
        return incoming?.let { normalizeSnapshot(it, null) }
    }
    if (existing.phase == OnboardingPhase.COMPLETED && incoming?.phase != OnboardingPhase.COMPLETED) {
        return normalizeSnapshot(existing, existing)
    }
    if (incoming == null) {
        return normalizeSnapshot(existing, existing)
    }
    if (incoming.phase == OnboardingPhase.COMPLETED && existing.phase != OnboardingPhase.COMPLETED) {
        return normalizeSnapshot(incoming, existing)
    }
    if (incoming.revision > existing.revision) {
        return normalizeSnapshot(incoming, existing)
    }
    if (incoming.revision < existing.revision) {
        return normalizeSnapshot(existing, existing)
    }
    return if (incoming.updatedAtMs >= existing.updatedAtMs) {
        normalizeSnapshot(incoming, existing)
    } else {
        normalizeSnapshot(existing, existing)
    }
}

private fun normalizeSnapshot(
    snapshot: OnboardingSnapshot,
    existing: OnboardingSnapshot?
): OnboardingSnapshot {
    val normalizedUpdatedAt = snapshot.updatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    return when (snapshot.phase) {
        OnboardingPhase.NEEDS_WORD_BOOK -> snapshot.copy(
            selectedWordBookId = null,
            updatedAtMs = normalizedUpdatedAt,
            completedAt = null
        )

        OnboardingPhase.NEEDS_STUDY_PLAN -> snapshot.copy(
            phase = OnboardingPhase.NEEDS_WORD_BOOK,
            selectedWordBookId = null,
            updatedAtMs = normalizedUpdatedAt,
            completedAt = null
        )

        OnboardingPhase.COMPLETED -> {
            val completedAt = snapshot.completedAt
                ?: existing?.completedAt
                ?: normalizedUpdatedAt
            snapshot.copy(
                updatedAtMs = normalizedUpdatedAt,
                completedAt = completedAt
            )
        }
    }
}
