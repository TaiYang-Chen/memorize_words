package com.chen.memorizewords.data.wordbook.local.room.wordbook

import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate.WordBookSyncStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate.WordBookSyncStateEntity
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateTrigger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WordBookSyncStateStore @Inject constructor(
    private val dao: WordBookSyncStateDao
) {

    private val lock = Any()
    private val stateByBookId = LinkedHashMap<Long, WordBookSyncStateEntity>()
    @Volatile
    private var cacheLoaded = false

    fun getState(bookId: Long): WordBookSyncStateEntity? = synchronized(lock) {
        ensureCacheLoadedLocked()
        stateByBookId[bookId]
    }

    fun getRemoteVersion(bookId: Long): Long = getState(bookId)?.remoteVersion ?: 0L

    fun updateRemoteVersions(versionByBookId: Map<Long, Long>) = synchronized(lock) {
        ensureCacheLoadedLocked()
        if (versionByBookId.isEmpty()) return
        val merged = versionByBookId.mapNotNull { (bookId, remoteVersion) ->
            if (bookId <= 0L || remoteVersion < 0L) return@mapNotNull null
            val current = stateByBookId[bookId] ?: WordBookSyncStateEntity(bookId = bookId)
            current.copy(remoteVersion = remoteVersion)
        }
        if (merged.isNotEmpty()) {
            merged.forEach { entity -> stateByBookId[entity.bookId] = entity }
            runDb { dao.upsertAll(merged) }
        }
    }

    fun getPendingTargetVersion(bookId: Long): Long = getState(bookId)?.pendingTargetVersion ?: 0L

    fun setPendingTargetVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(pendingTargetVersion = version.takeIf { value -> value > 0L }) }
    }

    fun getLocalVersion(bookId: Long): Long = getState(bookId)?.localVersion ?: 0L

    fun setLocalVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(localVersion = version.coerceAtLeast(0L)) }
    }

    fun getIgnoredVersion(bookId: Long): Long = getState(bookId)?.ignoredVersion ?: 0L

    fun setIgnoredVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(ignoredVersion = version.takeIf { value -> value > 0L }) }
    }

    fun getLastPromptedVersion(bookId: Long): Long = getState(bookId)?.lastPromptedVersion ?: 0L

    fun setLastPromptedVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(lastPromptedVersion = version.takeIf { value -> value > 0L }) }
    }

    fun getDeferredUntil(bookId: Long): Long = getState(bookId)?.deferredUntil ?: 0L

    fun setDeferredUntil(bookId: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(deferredUntil = timestamp.takeIf { value -> value > 0L }) }
    }

    fun setLastPrompt(
        bookId: Long,
        version: Long,
        timestamp: Long,
        trigger: WordBookUpdateTrigger
    ) = synchronized(lock) {
        upsert(bookId) {
            it.copy(
                lastPromptedVersion = version.takeIf { value -> value > 0L },
                lastPromptAt = timestamp.takeIf { value -> value > 0L },
                lastPromptSource = trigger
            )
        }
    }

    fun setLastCheckedAt(bookId: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(lastCheckedAt = timestamp.takeIf { value -> value > 0L }) }
    }

    fun markCompleted(bookId: Long, version: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) {
            val normalizedVersion = version.coerceAtLeast(0L)
            it.copy(
                localVersion = normalizedVersion,
                remoteVersion = maxOf(it.remoteVersion, normalizedVersion),
                pendingTargetVersion = null,
                ignoredVersion = if (it.ignoredVersion == normalizedVersion) null else it.ignoredVersion,
                deferredUntil = null,
                lastCompletedAt = timestamp.takeIf { value -> value > 0L },
                lastFailureReason = null
            )
        }
    }

    fun markFailed(bookId: Long, message: String) = synchronized(lock) {
        upsert(bookId) { it.copy(lastFailureReason = message) }
    }

    fun clearFailure(bookId: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(lastFailureReason = null) }
    }

    fun clearPendingTarget(bookId: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(pendingTargetVersion = null) }
    }

    fun deleteByBookIds(bookIds: List<Long>) = synchronized(lock) {
        ensureCacheLoadedLocked()
        if (bookIds.isNotEmpty()) {
            bookIds.forEach(stateByBookId::remove)
            runDb { dao.deleteByBookIds(bookIds) }
        }
    }

    fun clearLocalState() = synchronized(lock) {
        cacheLoaded = true
        stateByBookId.clear()
        runDb { dao.deleteAll() }
    }

    private fun upsert(bookId: Long, transform: (WordBookSyncStateEntity) -> WordBookSyncStateEntity) {
        ensureCacheLoadedLocked()
        if (bookId <= 0L) return
        val current = stateByBookId[bookId] ?: WordBookSyncStateEntity(bookId = bookId)
        val updated = transform(current)
        stateByBookId[bookId] = updated
        runDb { dao.upsert(updated) }
    }

    private fun ensureCacheLoadedLocked() {
        if (cacheLoaded) return
        runDb { dao.getAll() }
            .forEach { entity -> stateByBookId[entity.bookId] = entity }
        cacheLoaded = true
    }

    private fun <T> runDb(block: () -> T): T = runBlocking(Dispatchers.IO) {
        block()
    }
}
