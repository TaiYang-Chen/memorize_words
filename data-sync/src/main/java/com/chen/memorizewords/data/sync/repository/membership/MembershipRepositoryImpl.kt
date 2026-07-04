package com.chen.memorizewords.data.sync.repository.membership

import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.MembershipCheckInRewardDto
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.MembershipStatusDto
import com.chen.memorizewords.domain.account.model.membership.MembershipCheckInReward
import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import com.chen.memorizewords.domain.account.policy.currentMembershipTimeMillis
import com.chen.memorizewords.domain.account.policy.normalizeMembershipStatus
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.membership.MembershipRepository
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@Singleton
class MembershipRepositoryImpl @Inject constructor(
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val localAccountRepository: LocalAccountRepository,
    private val mmkv: MMKV,
    private val gson: Gson
) : MembershipRepository {

    private val statusCache = MembershipStatusCache(mmkv, gson)
    private val statusVersion = MutableStateFlow(0)

    override fun observeStatus() =
        combine(
            localAccountRepository.getUserFlow(),
            statusVersion,
            observeCurrentTime()
        ) { user, _, currentTimeMillis ->
            user?.userId?.let { userId ->
                statusCache.read(userId, currentTimeMillis)
            }
        }
            .distinctUntilChanged()

    override suspend fun getCachedStatus(): MembershipStatus? {
        val userId = localAccountRepository.getCurrentUserId() ?: return null
        return statusCache.read(userId)
    }

    override suspend fun refreshStatus(): Result<MembershipStatus> = runCatching {
        val userId = currentUserId()
        val status = remoteUserSyncDataSource.getMembershipStatus().getOrThrow().toDomain()
        saveStatus(userId, status)
        status
    }

    override suspend fun checkIn(): Result<MembershipCheckInReward> = runCatching {
        val userId = currentUserId()
        val reward = remoteUserSyncDataSource.checkInMembership().getOrThrow().toDomain()
        saveStatus(userId, reward.membership)
        reward
    }

    private suspend fun currentUserId(): Long {
        return localAccountRepository.getCurrentUserId()
            ?: throw IllegalStateException("User is not logged in")
    }

    private fun saveStatus(userId: Long, status: MembershipStatus) {
        statusCache.write(userId, status)
        statusVersion.value = statusVersion.value + 1
    }

    private fun observeCurrentTime(): Flow<Long> = flow {
        while (true) {
            emit(currentMembershipTimeMillis())
            delay(CURRENT_DATE_REFRESH_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    private companion object {
        const val CURRENT_DATE_REFRESH_INTERVAL_MS = 60_000L
    }
}

internal class MembershipStatusCache(
    private val keyValueStore: MembershipKeyValueStore,
    private val gson: Gson
) {
    constructor(mmkv: MMKV, gson: Gson) : this(MmkvMembershipKeyValueStore(mmkv), gson)

    fun read(userId: Long): MembershipStatus? {
        return read(userId, currentMembershipTimeMillis())
    }

    fun read(userId: Long, currentTimeMillis: Long): MembershipStatus? {
        val key = statusKey(userId)
        val json = keyValueStore.getString(key) ?: return null
        return runCatching {
            gson.fromJson(json, MembershipStatus::class.java)
        }.getOrElse {
            keyValueStore.remove(key)
            null
        }?.let { normalizeMembershipStatus(it, currentTimeMillis) }
    }

    fun write(userId: Long, status: MembershipStatus) {
        keyValueStore.putString(statusKey(userId), gson.toJson(status))
    }

    internal fun statusKey(userId: Long): String = "$KEY_STATUS_PREFIX$userId"

    private companion object {
        const val KEY_STATUS_PREFIX = "membership_status_"
    }
}

internal interface MembershipKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

private class MmkvMembershipKeyValueStore(
    private val mmkv: MMKV
) : MembershipKeyValueStore {
    override fun getString(key: String): String? = mmkv.decodeString(key, null)

    override fun putString(key: String, value: String) {
        mmkv.encode(key, value)
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }
}

private fun MembershipStatusDto.toDomain(): MembershipStatus {
    return MembershipStatus(
        level = level,
        active = active,
        validUntilDate = validUntilDate,
        validUntilAt = validUntilAt,
        remainingDays = remainingDays,
        totalGrantedDays = totalGrantedDays,
        todayCheckedIn = todayCheckedIn
    )
}

private fun MembershipCheckInRewardDto.toDomain(): MembershipCheckInReward {
    return MembershipCheckInReward(
        granted = granted,
        grantDays = grantDays,
        rewardDate = rewardDate,
        membership = membership.toDomain()
    )
}
