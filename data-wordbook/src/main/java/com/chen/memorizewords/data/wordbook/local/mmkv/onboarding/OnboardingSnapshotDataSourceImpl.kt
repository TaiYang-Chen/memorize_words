package com.chen.memorizewords.data.wordbook.local.mmkv.onboarding

import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class OnboardingSnapshotDataSourceImpl @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson
) : OnboardingSnapshotDataSource {

    private val snapshotFlows = ConcurrentHashMap<Long, MutableStateFlow<OnboardingSnapshot?>>()

    override fun getSnapshot(userId: Long): OnboardingSnapshot? = readSnapshot(userId)

    override fun observeSnapshot(userId: Long): Flow<OnboardingSnapshot?> {
        return stateFlowFor(userId)
    }

    override suspend fun saveSnapshot(userId: Long, snapshot: OnboardingSnapshot) {
        withContext(Dispatchers.IO) {
            mmkv.encode(keyOf(userId), gson.toJson(snapshot))
            stateFlowFor(userId).value = snapshot
        }
    }

    override suspend fun clearSnapshot(userId: Long) {
        withContext(Dispatchers.IO) {
            mmkv.removeValueForKey(keyOf(userId))
            stateFlowFor(userId).value = null
        }
    }

    private fun stateFlowFor(userId: Long): MutableStateFlow<OnboardingSnapshot?> {
        return snapshotFlows.getOrPut(userId) {
            MutableStateFlow(readSnapshot(userId))
        }
    }

    private fun readSnapshot(userId: Long): OnboardingSnapshot? {
        val raw = mmkv.decodeString(keyOf(userId), null) ?: return null
        return runCatching { gson.fromJson(raw, OnboardingSnapshot::class.java) }.getOrNull()
    }

    private fun keyOf(userId: Long): String = "onboarding_snapshot_$userId"
}
