package com.chen.memorizewords.domain.study
import kotlinx.coroutines.flow.Flow

data class StudyPlan(
    val dailyNewCount: Int,
    val dailyReviewCount: Int,
    val mode: StudyMode
)

enum class StudyMode {
    MEANING,
    SPELLING,
    LISTENING
}

data class StudyProgress(
    val businessDate: String,
    val newCompleted: Int,
    val reviewCompleted: Int,
    val durationMillis: Long
)

interface StudyRepository {
    fun observePlan(): Flow<StudyPlan>
    suspend fun savePlan(plan: StudyPlan)
    fun observeTodayProgress(): Flow<StudyProgress>
    suspend fun recordDuration(durationMillis: Long)
}
