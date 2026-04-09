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
        upsert(bookId) { it.copy(pendingTargetVersion = version) }
    }

    fun getLocalVersion(bookId: Long): Long = getState(bookId)?.localVersion ?: 0L

    fun setLocalVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(localVersion = version) }
    }

    fun getIgnoredVersion(bookId: Long): Long = getState(bookId)?.ignoredVersion ?: 0L

    fun setIgnoredVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(ignoredVersion = version) }
    }

    fun getLastPromptedVersion(bookId: Long): Long = getState(bookId)?.lastPromptedVersion ?: 0L

    fun setLastPromptedVersion(bookId: Long, version: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(lastPromptedVersion = version) }
    }

    fun getDeferredUntil(bookId: Long): Long = getState(bookId)?.deferredUntil ?: 0L

    fun setDeferredUntil(bookId: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(deferredUntil = timestamp) }
    }

    fun setLastPrompt(
        bookId: Long,
        version: Long,
        timestamp: Long,
        trigger: WordBookUpdateTrigger
    ) = synchronized(lock) {
        upsert(bookId) {
            it.copy(
                lastPromptedVersion = version,
                lastPromptAt = timestamp,
                lastPromptSource = trigger.name
            )
        }
    }

    fun setLastCheckedAt(bookId: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) { it.copy(lastCheckedAt = timestamp) }
    }

    fun markCompleted(bookId: Long, version: Long, timestamp: Long) = synchronized(lock) {
        upsert(bookId) {
            it.copy(
                localVersion = version,
                remoteVersion = maxOf(it.remoteVersion, version),
                pendingTargetVersion = 0L,
                ignoredVersion = if (it.ignoredVersion == version) 0L else it.ignoredVersion,
                deferredUntil = 0L,
                lastCompletedAt = timestamp,
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
        upsert(bookId) { it.copy(pendingTargetVersion = 0L) }
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
                    remoteVersion = remoteMap[bookId] ?: current.remoteVersion,
                    localVersion = localMap[bookId] ?: current.localVersion,
                    lastPromptedVersion = promptedMap[bookId] ?: current.lastPromptedVersion
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
