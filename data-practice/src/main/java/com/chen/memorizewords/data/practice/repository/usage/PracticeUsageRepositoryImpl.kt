package com.chen.memorizewords.data.practice.repository.usage

import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.remote.mapFailureToException
import com.chen.memorizewords.data.practice.remoteapi.api.practice.EvaluationUsageDto
import com.chen.memorizewords.data.practice.remoteapi.api.practice.PracticeUsageDto
import com.chen.memorizewords.data.practice.remoteapi.api.practice.PracticeUsageRequest
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.practice.usage.EvaluationPolicy
import com.chen.memorizewords.domain.practice.usage.EvaluationTier
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsage
import com.chen.memorizewords.domain.practice.usage.PracticeUsageRepository
import com.chen.memorizewords.domain.practice.usage.PracticeUsageState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PracticeUsageRepositoryImpl @Inject constructor(
    private val remote: PracticeUsageRequest,
    private val localAccountRepository: LocalAccountRepository
) : PracticeUsageRepository {
    private data class Cached(
        val userId: Long,
        val usage: PracticeUsage,
        val stale: Boolean,
        val serverOffsetMs: Long
    )

    private val cached = MutableStateFlow<Cached?>(null)
    private val loading = MutableStateFlow(false)
    private val refreshMutex = Mutex()

    override fun observe() = combine(
        localAccountRepository.getUserFlow(),
        cached,
        loading
    ) { user, cache, isLoading ->
        val userId = user?.userId ?: return@combine PracticeUsageState.Unknown
        if (cache?.userId != userId) {
            return@combine if (isLoading) PracticeUsageState.Loading else PracticeUsageState.Unknown
        }
        when {
            cache.stale || correctedNow(cache) >= cache.usage.evaluation.resetAtMs ->
                PracticeUsageState.Stale(cache.usage)
            cache.usage.evaluation.remaining <= 0 -> PracticeUsageState.Exhausted(cache.usage)
            else -> PracticeUsageState.Available(cache.usage)
        }
    }

    override suspend fun refresh(): Result<PracticeUsage> = refreshMutex.withLock {
        runCatching {
            val userId = localAccountRepository.getCurrentUserId()
                ?: throw IllegalStateException("User is not logged in")
            loading.value = true
            when (val result = remote.getUsage()) {
                is NetworkResult.Success -> result.data.toDomain().also { usage ->
                    cached.value = Cached(
                        userId = userId,
                        usage = usage,
                        stale = false,
                        serverOffsetMs = usage.serverTimeMs - System.currentTimeMillis()
                    )
                }
                is NetworkResult.Failure -> {
                    cached.value?.takeIf { it.userId == userId }?.let {
                        cached.value = it.copy(stale = true)
                    }
                    throw mapFailureToException(result)
                }
            }
        }.also { loading.value = false }
    }

    override suspend fun updateEvaluationUsage(usage: EvaluationUsage) {
        val userId = localAccountRepository.getCurrentUserId() ?: return
        val existingCache = cached.value?.takeIf { it.userId == userId }
        val updated = if (existingCache == null) {
            PracticeUsage(System.currentTimeMillis(), true, true, usage)
        } else {
            existingCache.usage.copy(
                serverTimeMs = System.currentTimeMillis() + existingCache.serverOffsetMs,
                evaluation = usage
            )
        }
        cached.value = Cached(
            userId = userId,
            usage = updated,
            stale = false,
            serverOffsetMs = existingCache?.serverOffsetMs ?: 0L
        )
    }

    override suspend fun clear() {
        cached.value = null
        loading.value = false
    }

    private fun correctedNow(cache: Cached): Long {
        return System.currentTimeMillis() + cache.serverOffsetMs
    }
}

private fun PracticeUsageDto.toDomain() = PracticeUsage(
    serverTimeMs = serverTimeMs,
    ttsAvailable = tts.available,
    ttsUnlimitedDaily = tts.unlimitedDaily,
    evaluation = evaluation.toDomain()
)

private fun EvaluationUsageDto.toDomain() = EvaluationUsage(
    tier = if (tier.equals("MEMBER", ignoreCase = true)) EvaluationTier.MEMBER else EvaluationTier.FREE,
    dailyLimit = dailyLimit,
    used = used,
    remaining = remaining,
    resetAtMs = resetAtMs,
    policy = EvaluationPolicy(policy.freeDailyLimit, policy.memberDailyLimit)
)
