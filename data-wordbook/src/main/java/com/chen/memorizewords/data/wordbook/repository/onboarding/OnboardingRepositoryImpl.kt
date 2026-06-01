package com.chen.memorizewords.data.wordbook.repository.onboarding

import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.domain.sync.OnboardingStateSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.google.gson.Gson
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
    private val onboardingSnapshotDataSource: OnboardingSnapshotDataSource,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
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
                updatedAt = now,
                completedAt = current.completedAt ?: now
            )
            onboardingSnapshotDataSource.saveSnapshot(userId, next)
            enqueueSnapshotSync(userId, next)
            next
        }
    }
    private suspend fun enqueueSnapshotSync(userId: Long, snapshot: OnboardingSnapshot) {
        SyncOutboxWriter.enqueueLatest(
            bizType = OutboxTopic.ONBOARDING_STATE,
            bizKey = "onboarding_state:$userId",
            operation = SyncOperation.UPSERT,
            payload = gson.toJson(
                OnboardingStateSyncPayload(
                    phase = snapshot.phase.name,
                    selectedWordBookId = snapshot.selectedWordBookId,
                    revision = snapshot.revision,
                    updatedAt = snapshot.updatedAt,
                    completedAt = snapshot.completedAt
                )
            ),
            updatedAt = snapshot.updatedAt
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
    return if (incoming.updatedAt >= existing.updatedAt) {
        normalizeSnapshot(incoming, existing)
    } else {
        normalizeSnapshot(existing, existing)
    }
}

private fun normalizeSnapshot(
    snapshot: OnboardingSnapshot,
    existing: OnboardingSnapshot?
): OnboardingSnapshot {
    val normalizedUpdatedAt = snapshot.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    return when (snapshot.phase) {
        OnboardingPhase.NEEDS_WORD_BOOK -> snapshot.copy(
            selectedWordBookId = null,
            updatedAt = normalizedUpdatedAt,
            completedAt = null
        )

        OnboardingPhase.NEEDS_STUDY_PLAN -> snapshot.copy(
            phase = OnboardingPhase.NEEDS_WORD_BOOK,
            selectedWordBookId = null,
            updatedAt = normalizedUpdatedAt,
            completedAt = null
        )

        OnboardingPhase.COMPLETED -> {
            val completedAt = snapshot.completedAt
                ?: existing?.completedAt
                ?: normalizedUpdatedAt
            snapshot.copy(
                updatedAt = normalizedUpdatedAt,
                completedAt = completedAt
            )
        }
    }
}
