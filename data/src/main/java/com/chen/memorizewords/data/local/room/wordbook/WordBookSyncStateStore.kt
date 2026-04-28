package com.chen.memorizewords.data.local.room.wordbook

import com.chen.memorizewords.data.local.room.model.wordbook.syncstate.WordBookSyncStateDao
import com.chen.memorizewords.data.local.room.model.wordbook.syncstate.WordBookSyncStateEntity
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateTrigger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WordBookSyncStateStore @Inject constructor(
    private val dao: WordBookSyncStateDao,
    private val mmkv: MMKV,
    private val gson: Gson
) {

    private val lock = Any()
    private val stateByBookId = LinkedHashMap<Long, WordBookSyncStateEntity>()
    @Volatile
    private var cacheLoaded = false
    @Volatile
    private var migrated = false

    fun getState(bookId: Long): WordBookSyncStateEntity? = synchronized(lock) {
        ensureLegacyMigratedLocked()
        stateByBookId[bookId]
    }

    fun getRemoteVersion(bookId: Long): Long = getState(bookId)?.remoteVersion ?: 0L

    fun updateRemoteVersions(versionByBookId: Map<Long, Long>) = synchronized(lock) {
        ensureLegacyMigratedLocked()
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
        ensureLegacyMigratedLocked()
        if (bookIds.isNotEmpty()) {
            bookIds.forEach(stateByBookId::remove)
            runDb { dao.deleteByBookIds(bookIds) }
        }
    }

    fun clearLocalState() = synchronized(lock) {
        cacheLoaded = true
        migrated = true
        stateByBookId.clear()
        runDb { dao.deleteAll() }
        clearLegacyKeys()
    }

    private fun upsert(bookId: Long, transform: (WordBookSyncStateEntity) -> WordBookSyncStateEntity) {
        ensureLegacyMigratedLocked()
        if (bookId <= 0L) return
        val current = stateByBookId[bookId] ?: WordBookSyncStateEntity(bookId = bookId)
        val updated = transform(current)
        stateByBookId[bookId] = updated
        runDb { dao.upsert(updated) }
    }

    private fun ensureLegacyMigratedLocked() {
        ensureCacheLoadedLocked()
        if (migrated) return
        val remoteMap = loadLegacyMap(KEY_REMOTE_VERSION)
        val localMap = loadLegacyMap(KEY_LOCAL_VERSION)
        val promptedMap = loadLegacyMap(KEY_LAST_PROMPTED_VERSION)
        if (remoteMap.isNotEmpty() || localMap.isNotEmpty() || promptedMap.isNotEmpty()) {
            val allBookIds = (remoteMap.keys + localMap.keys + promptedMap.keys).toSet()
            val merged = allBookIds.map { bookId ->
                val current = stateByBookId[bookId] ?: WordBookSyncStateEntity(bookId = bookId)
                current.copy(
                    remoteVersion = (remoteMap[bookId] ?: current.remoteVersion).coerceAtLeast(0L),
                    localVersion = (localMap[bookId] ?: current.localVersion).coerceAtLeast(0L),
                    lastPromptedVersion = (promptedMap[bookId] ?: current.lastPromptedVersion)
                        ?.takeIf { it > 0L }
                )
            }
            merged.forEach { entity -> stateByBookId[entity.bookId] = entity }
            runDb { dao.upsertAll(merged) }
        }
        clearLegacyKeys()
        migrated = true
    }

    private fun ensureCacheLoadedLocked() {
        if (cacheLoaded) return
        runDb { dao.getAll() }
            .forEach { entity -> stateByBookId[entity.bookId] = entity }
        cacheLoaded = true
    }

    private fun loadLegacyMap(key: String): Map<Long, Long> {
        val json = mmkv.decodeString(key, null)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val raw: Map<String, Long> = gson.fromJson(json, type) ?: emptyMap()
            raw.mapNotNull { (id, value) ->
                id.toLongOrNull()?.let { it to value }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun clearLegacyKeys() {
        mmkv.removeValueForKey(KEY_REMOTE_VERSION)
        mmkv.removeValueForKey(KEY_LOCAL_VERSION)
        mmkv.removeValueForKey(KEY_LAST_PROMPTED_VERSION)
    }

    private fun <T> runDb(block: () -> T): T = runBlocking(Dispatchers.IO) {
        block()
    }

    private companion object {
        private const val KEY_REMOTE_VERSION = "wordbook_remote_updated_at"
        private const val KEY_LOCAL_VERSION = "wordbook_local_synced_updated_at"
        private const val KEY_LAST_PROMPTED_VERSION = "wordbook_last_notified_updated_at"
    }
}
