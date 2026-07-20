package com.chen.memorizewords.data.floating.local

import com.chen.memorizewords.domain.floating.model.FloatingActivationSource
import com.chen.memorizewords.domain.floating.model.PendingFloatingActivation
import com.chen.memorizewords.domain.floating.repository.FloatingActivationStateRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FloatingActivationStateStore @Inject constructor(
    private val mmkv: MMKV
) : FloatingActivationStateRepository {
    private val gson = Gson()
    private val processMutex = Mutex()
    private val state = MutableStateFlow(readPendingFromStorage())

    override fun observePending(): Flow<PendingFloatingActivation?> = merge(
        state.asStateFlow(),
        flow {
            while (currentCoroutineContext().isActive) {
                delay(EXTERNAL_PROCESS_REFRESH_INTERVAL_MS)
                emit(getPending())
            }
        }
    ).distinctUntilChanged()

    override suspend fun getPending(): PendingFloatingActivation? = processMutex.withLock {
        refreshFromStorage()
    }

    override suspend fun savePending(pending: PendingFloatingActivation) = processMutex.withLock {
        val payload = gson.toJson(
            StoredPendingActivation(
                requestId = pending.requestId,
                targetPackId = pending.targetPackId,
                source = pending.source.name,
                createdAtMs = pending.createdAtMs,
                committedAtMs = pending.committedAtMs
            )
        )
        withMmkvFileLock {
            check(mmkv.encode(KEY_PAYLOAD, payload)) {
                "Failed to persist floating activation request"
            }
            removeLegacyKeys()
        }
        state.value = pending
    }

    override suspend fun clearPending(requestId: String?): Boolean = processMutex.withLock {
        var latest: PendingFloatingActivation? = null
        val cleared = withMmkvFileLock {
            mmkv.checkContentChangedByOuterProcess()
            latest = readPendingLocked()
            if (requestId != null && latest?.requestId != requestId) {
                false
            } else {
                check(mmkv.encode(KEY_PAYLOAD, "")) {
                    "Failed to clear floating activation request"
                }
                removeLegacyKeys()
                latest = null
                true
            }
        }
        state.value = latest
        cleared
    }

    private fun refreshFromStorage(): PendingFloatingActivation? {
        val latest = readPendingFromStorage()
        if (latest != state.value) state.value = latest
        return latest
    }

    private fun readPendingFromStorage(): PendingFloatingActivation? = withMmkvFileLock {
        mmkv.checkContentChangedByOuterProcess()
        readPendingLocked()
    }

    private fun readPendingLocked(): PendingFloatingActivation? {
        if (mmkv.containsKey(KEY_PAYLOAD)) {
            val payload = mmkv.decodeString(KEY_PAYLOAD).orEmpty()
            if (payload.isBlank()) return null
            return runCatching {
                gson.fromJson(payload, StoredPendingActivation::class.java).toDomain()
            }.getOrNull()
        }
        return readLegacyPending()
    }

    private inline fun <T> withMmkvFileLock(block: () -> T): T {
        mmkv.lock()
        return try {
            block()
        } finally {
            mmkv.unlock()
        }
    }

    private fun readLegacyPending(): PendingFloatingActivation? {
        val requestId = mmkv.decodeString(KEY_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) return null
        val source = runCatching {
            FloatingActivationSource.valueOf(mmkv.decodeString(KEY_SOURCE).orEmpty())
        }.getOrDefault(FloatingActivationSource.HOME)
        return PendingFloatingActivation(
            requestId = requestId,
            targetPackId = mmkv.decodeString(KEY_TARGET_PACK_ID)?.takeIf { it.isNotBlank() },
            source = source,
            createdAtMs = mmkv.decodeLong(KEY_CREATED_AT, 0L),
            committedAtMs = null
        )
    }

    private fun removeLegacyKeys() {
        listOf(KEY_REQUEST_ID, KEY_TARGET_PACK_ID, KEY_SOURCE, KEY_CREATED_AT)
            .forEach(mmkv::removeValueForKey)
    }

    private data class StoredPendingActivation(
        val requestId: String,
        val targetPackId: String?,
        val source: String,
        val createdAtMs: Long,
        val committedAtMs: Long? = null
    ) {
        fun toDomain(): PendingFloatingActivation {
            require(requestId.isNotBlank())
            return PendingFloatingActivation(
                requestId = requestId,
                targetPackId = targetPackId?.takeIf(String::isNotBlank),
                source = runCatching { FloatingActivationSource.valueOf(source) }
                    .getOrDefault(FloatingActivationSource.HOME),
                createdAtMs = createdAtMs,
                committedAtMs = committedAtMs
            )
        }
    }

    private companion object {
        const val KEY_PAYLOAD = "floating_activation_payload_v2"
        const val KEY_REQUEST_ID = "floating_activation_request_id"
        const val KEY_TARGET_PACK_ID = "floating_activation_target_pack_id"
        const val KEY_SOURCE = "floating_activation_source"
        const val KEY_CREATED_AT = "floating_activation_created_at"
        const val EXTERNAL_PROCESS_REFRESH_INTERVAL_MS = 750L
    }
}
