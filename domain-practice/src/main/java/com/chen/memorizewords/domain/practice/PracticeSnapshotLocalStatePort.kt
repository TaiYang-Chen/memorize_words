package com.chen.memorizewords.domain.practice

data class PracticeDailyDurationSnapshot(
    val date: String,
    val totalDurationMs: Long,
    val updatedAtMs: Long
)

interface PracticeSnapshotLocalStatePort {
    suspend fun overwriteDurationsFromRemote(durations: List<PracticeDailyDurationSnapshot>)
    suspend fun overwriteSessionsFromRemote(records: List<PracticeSessionRecord>)
}
