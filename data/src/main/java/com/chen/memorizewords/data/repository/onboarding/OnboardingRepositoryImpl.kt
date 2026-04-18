package com.chen.memorizewords.data.repository.onboarding

import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.repository.sync.OnboardingStateSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxStore
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.domain.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.repository.onboarding.OnboardingRepository
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
    private val authLocalDataSource: AuthLocalDataSource,
    private val onboardingSnapshotDataSource: OnboardingSnapshotDataSource,
    private val wordBookDao: WordBookDao,
    private val syncOutboxStore: SyncOutboxStore,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : OnboardingRepository {

    override fun getCurrentSnapshot(): OnboardingSnapshot {
        val userId = authLocalDataSource.getUserId() ?: return DEFAULT_SNAPSHOT
        return normalizeSnapshot(
            snapshot = onboardingSnapshotDataSource.getSnapshot(userId) ?: DEFAULT_SNAPSHOT,
            existing = null
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> {
        return authLocalDataSource.getUserFlow()
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
            val compat = if (existing == null && snapshot == null) inferCompatSnapshot() else null
            val resolved = resolvePreferredSnapshot(
                existing = existing,
                incoming = snapshot ?: compat
            ) ?: DEFAULT_SNAPSHOT
            onboardingSnapshotDataSource.saveSnapshot(userId, resolved)
            if (compat != null) {
                enqueueSnapshotSync(userId, resolved)
            }
        }
    }

    override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) {
        if (snapshot == null) return
        withContext(Dispatchers.IO) {
            val userId = authLocalDataSource.getUserId() ?: return@withContext
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

    private suspend fun inferCompatSnapshot(): OnboardingSnapshot {
        val currentWordBook = wordBookDao.getCurrentWordBook()
        if (currentWordBook != null) {
            val now = System.currentTimeMillis()
            return OnboardingSnapshot(
                phase = OnboardingPhase.COMPLETED,
                selectedWordBookId = currentWordBook.id,
                revision = 1L,
                updatedAt = now,
                completedAt = now
            )
        }
        return DEFAULT_SNAPSHOT
    }

    private suspend fun enqueueSnapshotSync(userId: Long, snapshot: OnboardingSnapshot) {
        syncOutboxStore.enqueueLatest(
            bizType = SyncOutboxBizType.ONBOARDING_STATE,
            bizKey = "onboarding_state:$userId",
            operation = SyncOutboxOperation.UPSERT,
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
        syncOutboxWorkScheduler.scheduleDrain()
    }

    private fun requireCurrentUserId(): Long {
        return authLocalDataSource.getUserId()
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
