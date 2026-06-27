package com.chen.memorizewords.data.sync.bootstrap

import com.chen.memorizewords.domain.sync.model.HomeStartupSnapshot
import com.chen.memorizewords.domain.sync.repository.HomeStartupSnapshotRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class HomeStartupSnapshotStore @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson
) : HomeStartupSnapshotRepository {

    private val lock = Any()
    private val snapshotFlow = MutableStateFlow(readCurrentSnapshot())

    override fun getSnapshot(): HomeStartupSnapshot? = snapshotFlow.value

    override fun observeSnapshot(): Flow<HomeStartupSnapshot?> = snapshotFlow.asStateFlow()

    override suspend fun saveSnapshot(snapshot: HomeStartupSnapshot) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                mmkv.encode(KEY_CURRENT_USER_ID, snapshot.userId)
                mmkv.encode(keyOf(snapshot.userId), gson.toJson(snapshot))
                snapshotFlow.value = snapshot
            }
        }
    }

    override suspend fun clearSnapshot() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val currentUserId = mmkv.decodeLong(KEY_CURRENT_USER_ID, 0L)
                    .takeIf { it > 0L }
                    ?: snapshotFlow.value?.userId
                currentUserId?.let { mmkv.removeValueForKey(keyOf(it)) }
                mmkv.removeValueForKey(KEY_CURRENT_USER_ID)
                snapshotFlow.value = null
            }
        }
    }

    private fun readCurrentSnapshot(): HomeStartupSnapshot? {
        val userId = mmkv.decodeLong(KEY_CURRENT_USER_ID, 0L).takeIf { it > 0L } ?: return null
        val raw = mmkv.decodeString(keyOf(userId), null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            gson.fromJson(raw, HomeStartupSnapshot::class.java)
        }.getOrNull()
            ?.takeIf { it.userId == userId }
    }

    private fun keyOf(userId: Long): String = "$KEY_SNAPSHOT_PREFIX$userId"

    private companion object {
        private const val KEY_CURRENT_USER_ID = "home_startup_current_user_id"
        private const val KEY_SNAPSHOT_PREFIX = "home_startup_snapshot_"
    }
}
