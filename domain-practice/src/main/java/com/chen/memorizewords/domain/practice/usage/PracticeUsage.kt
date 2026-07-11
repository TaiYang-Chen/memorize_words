package com.chen.memorizewords.domain.practice.usage

import kotlinx.coroutines.flow.Flow

data class EvaluationPolicy(val freeDailyLimit: Int, val memberDailyLimit: Int)

data class EvaluationUsage(
    val tier: EvaluationTier,
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int,
    val resetAtMs: Long,
    val policy: EvaluationPolicy
)

enum class EvaluationTier { FREE, MEMBER }

data class PracticeUsage(
    val serverTimeMs: Long,
    val ttsAvailable: Boolean,
    val ttsUnlimitedDaily: Boolean,
    val evaluation: EvaluationUsage
)

sealed interface PracticeUsageState {
    data object Loading : PracticeUsageState
    data object Unknown : PracticeUsageState
    data class Available(val usage: PracticeUsage) : PracticeUsageState
    data class Stale(val usage: PracticeUsage) : PracticeUsageState
    data class Exhausted(val usage: PracticeUsage) : PracticeUsageState
}

interface PracticeUsageRepository {
    fun observe(): Flow<PracticeUsageState>
    suspend fun refresh(): Result<PracticeUsage>
    suspend fun updateEvaluationUsage(usage: EvaluationUsage)
    suspend fun clear()
}
